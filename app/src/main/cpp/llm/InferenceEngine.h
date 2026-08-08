#pragma once

#include <string>
#include "llama.h"
#include <vector>
#include <chat.h>
#include "sampling.h"

class InferenceEngine {

public:

    InferenceEngine();

    ~InferenceEngine();

    bool loadModel(const std::string & modelPath);

    std::string generate(const std::string & prompt);

    void release();

private:

    llama_model * model = nullptr;

    llama_context * ctx = nullptr;

    common_sampler * sampler = nullptr;

    const llama_vocab * vocab = nullptr;

    llama_batch batch;

    common_chat_templates_ptr chat_templates;

    llama_pos current_position = 0;

    llama_pos system_prompt_position = 0;

    llama_pos stop_generation_position = 0;


    std::vector<llama_chat_message> messages;

    std::vector<char> formatted;

    int previousTemplateLength = 0;
};