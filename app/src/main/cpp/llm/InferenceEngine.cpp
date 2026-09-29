#include "InferenceEngine.h"
#include <android/log.h>
#include <unistd.h>
#include "sampling.h"

#define TAG "LLM"

InferenceEngine::InferenceEngine() {}

InferenceEngine::~InferenceEngine() {
    release();
}

bool InferenceEngine::loadModel(const std::string &modelPath) {

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Loading model...");

    llama_backend_init();

    llama_model_params model_params =
            llama_model_default_params();

    model = llama_model_load_from_file(
            modelPath.c_str(),
            model_params);

    if (!model) {
        __android_log_print(ANDROID_LOG_ERROR,
                            TAG,
                            "Failed loading model");
        return false;
    }

    llama_context_params ctx_params =
            llama_context_default_params();

    ctx_params.n_ctx = 1024;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    ctx = llama_init_from_model(
            model,
            ctx_params);

    if (!ctx) {

        llama_model_free(model);

        model = nullptr;

        return false;
    }

    vocab = llama_model_get_vocab(model);
    batch = llama_batch_init(512, 0, 1);

    chat_templates = common_chat_templates_init(model, "");

    common_params_sampling sampling;

    sampling.temp = 0.3f;

    sampler = common_sampler_init(model, sampling);

    if (!sampler) {

        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "Failed to create sampler");

        return false;
    }


    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "InferenceEngine Ready");

    return true;
}



static int decode_tokens_in_batches(

        llama_context *context,
        llama_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {

    for (int i = 0; i < (int) tokens.size(); i += 512) {
        const int cur_batch_size = std::min((int) tokens.size() - i, 512);
        common_batch_clear(batch);

        // Shift context if current batch cannot fit into the context
        if (start_pos + i + cur_batch_size >= llama_n_ctx(context) - 4) {
            return 1;
        }

        // Add tokens to the batch with proper positions
        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == tokens.size() - 1);
            common_batch_add(batch, token_id, position, {0}, want_logit);
        }

        // Decode this batch
        __android_log_print(
                ANDROID_LOG_INFO,
                TAG,
                "Calling llama_decode");

        int ret = llama_decode(context, batch);

        __android_log_print(
                ANDROID_LOG_INFO,
                TAG,
                "llama_decode returned %d",
                ret);

        if (ret != 0) {
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    TAG,
                    "Decode failed");
            return 1;
        }
    }
    return 0;
}

std::string InferenceEngine::generate(
        const std::string & userPrompt) {
    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "Generate() entered on thread %ld",
            (long)gettid());

    // Each query from PromptBuilder contains complete, freshly perceived scene context.
    // Resetting context and KV memory prevents context bloat, memory leaks, and unbounded slowdowns.
    clearHistory();

    const char * tmpl =
            llama_model_chat_template(
                    model,
                    nullptr);

    messages.push_back({
                               "user",
                               strdup(userPrompt.c_str())
                       });

    int newLength =
            llama_chat_apply_template(
                    tmpl,
                    messages.data(),
                    messages.size(),
                    true,
                    formatted.data(),
                    formatted.size());

    if (newLength > (int) formatted.size()) {

        formatted.resize(newLength);

        newLength =
                llama_chat_apply_template(
                        tmpl,
                        messages.data(),
                        messages.size(),
                        true,
                        formatted.data(),
                        formatted.size());
    }

    if (newLength < 0) {

        return "Template Error";
    }

    std::string prompt(

            formatted.begin() + previousTemplateLength,

            formatted.begin() + newLength
    );
    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "Prompt length = %d",
            (int)prompt.size());

    std::string response;

    llama_tokens promptTokens =
            common_tokenize(
                    ctx,
                    prompt,
                    true,
                    true);
    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "Prompt tokens = %d",
            (int)promptTokens.size());

    llama_token newToken;

    if (decode_tokens_in_batches(
            ctx,
            batch,
            promptTokens,
            current_position,
            true)) {

        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "Decode failed");

        return "Decode failed";
    }

    current_position += promptTokens.size();

    // Cap output to 60 tokens to keep response time short (1-2s) and answers concise for TTS
    const int max_tokens = 60;
    int tokens_generated = 0;

    while (tokens_generated < max_tokens) {
        int n_ctx = llama_n_ctx(ctx);

        int n_ctx_used =
                llama_memory_seq_pos_max(
                        llama_get_memory(ctx),
                        0
                ) + 1;

        if (n_ctx_used + batch.n_tokens > n_ctx) {

            break;
        }

        newToken =
                common_sampler_sample(
                        sampler,
                        ctx,
                        -1);

        common_sampler_accept(
                sampler,
                newToken,
                true);

        if (llama_vocab_is_eog(vocab, newToken))
            break;

        response +=
                common_token_to_piece(
                        ctx,
                        newToken);

        common_batch_clear(batch);

        common_batch_add(
                batch,
                newToken,
                current_position,
                {0},
                true);

        current_position++;
        tokens_generated++;

        if (llama_decode(ctx, batch) != 0) {

            __android_log_print(
                    ANDROID_LOG_ERROR,
                    TAG,
                    "Token decode failed");

            break;
        }
    }
    messages.push_back({
                               "assistant",
                               strdup(response.c_str())
                       });

    previousTemplateLength =
            newLength;


    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "Response = %s",
            response.c_str());
    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "Generate() exiting");
    return response;
}

void InferenceEngine::clearHistory() {
    for (auto & msg : messages) {
        if (msg.content) {
            free((void *) msg.content);
        }
    }
    messages.clear();
    formatted.clear();
    previousTemplateLength = 0;

    if (ctx) {
        llama_memory_seq_rm(llama_get_memory(ctx), -1, -1, -1);
    }
    current_position = 0;
}

void InferenceEngine::release() {
    clearHistory();

    if (sampler) {

        common_sampler_free(sampler);

        sampler = nullptr;
    }

    if (ctx) {

        llama_free(ctx);

        ctx = nullptr;
    }

    if (model) {

        llama_model_free(model);

        model = nullptr;
    }

    llama_backend_free();
}