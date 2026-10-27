package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.sin

class AmbientSoundPlayer {
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var currentSound: String = "none"
    private var currentVolume: Float = 0.5f

    fun play(sound: String, volume: Float = currentVolume) {
        currentSound = sound
        currentVolume = volume.coerceIn(0f, 1f)

        stop()
        if (sound == "none") return

        val sampleRate = 22050
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(currentVolume)
            audioTrack?.play()

            playbackJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                val random = Random()
                var phase = 0.0

                while (isActive) {
                    when (sound) {
                        "rain" -> {
                            // Soft filtered pinkish noise with intermittent droplet bursts
                            for (i in buffer.indices) {
                                val white = (random.nextGaussian() * 3200).toInt()
                                val drop = if (random.nextInt(1200) == 0) (random.nextInt(12000) - 6000) else 0
                                buffer[i] = (white + drop).coerceIn(-32767, 32767).toShort()
                            }
                        }
                        "whitenoise" -> {
                            // Gentle pure white noise
                            for (i in buffer.indices) {
                                val sample = (random.nextGaussian() * 3000).toInt()
                                buffer[i] = sample.coerceIn(-32767, 32767).toShort()
                            }
                        }
                        "deepfocus" -> {
                            // Soothing 136Hz binaural/warm sine + brown noise
                            val freq = 136.0
                            val delta = 2.0 * Math.PI * freq / sampleRate
                            for (i in buffer.indices) {
                                phase += delta
                                val sine = (sin(phase) * 6000).toInt()
                                val warmHum = (random.nextGaussian() * 1800).toInt()
                                buffer[i] = (sine + warmHum).coerceIn(-32767, 32767).toShort()
                            }
                        }
                        "stream" -> {
                            // Gentle undulating babbling stream
                            val freq = 220.0
                            val delta = 2.0 * Math.PI * freq / sampleRate
                            for (i in buffer.indices) {
                                phase += delta
                                val wave = (sin(phase) * 2500 * (1.0 + 0.5 * sin(phase * 0.05))).toInt()
                                val waterHiss = (random.nextGaussian() * 2200).toInt()
                                buffer[i] = (wave + waterHiss).coerceIn(-32767, 32767).toShort()
                            }
                        }
                        else -> {
                            for (i in buffer.indices) buffer[i] = 0
                        }
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(currentVolume)
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
    }
}
