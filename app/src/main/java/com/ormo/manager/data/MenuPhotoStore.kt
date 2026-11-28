package com.ormo.manager.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 메뉴 사진 올리기. 백엔드를 붙일 때 구현을 바꾼다. 성공하면 사진을 가리키는 값(지금은 파일 경로)을 준다. */
interface MenuPhotoStore {
    suspend fun upload(jpeg: ByteArray): Result<String>
}

/**
 * 서버 연동 전: 앱 캐시에 저장한다(메뉴도 앱을 다시 켜면 사라진다).
 * [failing]은 개발 빌드의 화면 확인용이다.
 */
class LocalMenuPhotoStore(private val directory: File, var failing: Boolean = false) : MenuPhotoStore {

    override suspend fun upload(jpeg: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(!failing) { "upload unavailable" }
            directory.mkdirs()
            File(directory, "${UUID.randomUUID()}.jpg").apply { writeBytes(jpeg) }.absolutePath
        }
    }
}
