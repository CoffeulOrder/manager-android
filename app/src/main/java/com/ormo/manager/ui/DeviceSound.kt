package com.ormo.manager.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator

/**
 * 태블릿의 미디어 소리. 알림음은 기기 기본 알림음을 미디어 음량으로 울린다.
 * 음량이 0이거나 블루투스로 소리가 나가면 "꺼짐"으로 본다(Figma 1.2.3).
 */
class DeviceSound(context: Context) {

    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    private val ringtone = RingtoneManager
        .getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        ?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

    /** 기본 알림음이 없는 기기에서 쓰는 비프음 */
    private val tone: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull()
    }

    fun isOff(): Boolean =
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 ||
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

    /** 미디어 음량을 올린다. 방해 금지 설정 등으로 막히면 그대로 둔다. */
    fun turnUp() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 7 / 10).coerceAtLeast(1), 0)
        }
    }

    fun playAlert() {
        val current = ringtone
        if (current != null) {
            current.stop()
            current.play()
        } else {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        }
    }

    fun stop() {
        ringtone?.stop()
        tone?.stopTone()
    }
}
