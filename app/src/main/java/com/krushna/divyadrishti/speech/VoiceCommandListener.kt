package com.krushna.divyadrishti.speech

interface VoiceCommandListener {

    fun onCommandRecognized(command: String)

    fun onError(error: String)

}