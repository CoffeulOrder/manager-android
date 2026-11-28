package com.ormo.manager.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.CropWindow
import com.ormo.manager.domain.PhotoProblem
import com.ormo.manager.domain.checkPhoto
import com.ormo.manager.domain.formatWon
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SlotSize = 164.dp

sealed interface PhotoUpload {
    data object Idle : PhotoUpload
    data object Uploading : PhotoUpload

    /** 같은 사진으로 다시 올릴 수 있다. */
    class Failed(val jpeg: ByteArray) : PhotoUpload
}

/**
 * 메뉴 편집 패널의 사진 상태(Figma 3.2.1 "사진 올리기"). 고르기 → 맞추기 → 올리기.
 * 패널이 닫히면 올리던 것도 멈춘다.
 */
class MenuPhotoState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val upload: () -> suspend (ByteArray) -> Result<String>,
    private val onPhoto: (String?) -> Unit,
) {
    var menuOpen by mutableStateOf(false)

    /** 자르는 중인 원본 */
    var cropping by mutableStateOf<Bitmap?>(null)
        private set

    var progress by mutableStateOf<PhotoUpload>(PhotoUpload.Idle)
        private set

    var problem by mutableStateOf<PhotoProblem?>(null)
        private set

    /** 사진 칸의 화면 위치(px). 메뉴를 그 옆에 연다. */
    var slotPosition by mutableStateOf(Offset.Zero)

    val uploading: Boolean get() = progress == PhotoUpload.Uploading

    /** 사진 칸을 눌렀을 때: 올리기에 실패했으면 다시 올리고, 아니면 메뉴를 연다. */
    fun onSlotClick() {
        when (val current = progress) {
            PhotoUpload.Uploading -> Unit
            is PhotoUpload.Failed -> start(current.jpeg)
            PhotoUpload.Idle -> menuOpen = true
        }
    }

    fun onPicked(uri: Uri, fromAlbum: Boolean) {
        scope.launch {
            val (found, bitmap) = withContext(Dispatchers.IO) {
                // 방금 찍은 사진은 줄여서 올리므로 크기를 따지지 않는다.
                val invalid = if (fromAlbum) {
                    checkPhoto(context.contentResolver.getType(uri), photoSize(context, uri))
                } else {
                    null
                }
                val decoded = if (invalid == null) decodePhoto(context, uri) else null
                val result = invalid ?: PhotoProblem.UNREADABLE.takeIf { decoded == null }
                result to decoded
            }
            problem = found
            cropping = bitmap
        }
    }

    fun onCropped(window: CropWindow) {
        val source = cropping ?: return
        cropping = null
        scope.launch { start(withContext(Dispatchers.Default) { cropToJpeg(source, window) }) }
    }

    fun onPickAgain() {
        cropping = null
        menuOpen = true
    }

    fun onCropDismiss() {
        cropping = null
    }

    fun onRemove() {
        problem = null
        progress = PhotoUpload.Idle
        onPhoto(null)
    }

    private fun start(jpeg: ByteArray) {
        progress = PhotoUpload.Uploading
        scope.launch {
            upload()(jpeg).fold(
                onSuccess = {
                    onPhoto(it)
                    progress = PhotoUpload.Idle
                },
                onFailure = { progress = PhotoUpload.Failed(jpeg) },
            )
        }
    }
}

@Composable
fun rememberMenuPhotoState(
    upload: suspend (ByteArray) -> Result<String>,
    onPhoto: (String?) -> Unit,
): MenuPhotoState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentUpload by rememberUpdatedState(upload)
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    return remember { MenuPhotoState(context, scope, { currentUpload }, { currentOnPhoto(it) }) }
}

/** "사진" 칸 164: 비어 있음 · 올리는 중 · 실패 · 사진 */
@Composable
fun PhotoSlot(state: MenuPhotoState, photo: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val failed = state.progress is PhotoUpload.Failed || state.problem != null
    val description = stringResource(if (photo == null) R.string.photo_add else R.string.photo_change)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.width(SlotSize)) {
        Text(stringResource(R.string.photo_label), style = OrmoType.BodyMdSemibold, color = OrmoColors.TextSecondary)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(SlotSize)
                .clip(shape)
                .background(if (failed) OrmoColors.FillDangerWeak else OrmoColors.BackgroundLower)
                .border(1.dp, if (failed) OrmoColors.FillDanger else OrmoColors.Border, shape)
                .clickable(role = Role.Button, onClick = state::onSlotClick)
                .semantics { contentDescription = description },
        ) {
            when {
                state.uploading -> {
                    Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        Text(
                            stringResource(R.string.photo_uploading),
                            style = OrmoType.BodySmSemibold,
                            color = OrmoColors.TextOnFill,
                        )
                    }
                }
                failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(painterResource(R.drawable.ic_warning), contentDescription = null)
                    Text(
                        stringResource(R.string.photo_retry),
                        style = OrmoType.BodySmBold,
                        color = OrmoColors.TextDanger,
                    )
                }
                photo != null -> {
                    PhotoImage(photo, Modifier.fillMaxSize())
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(OrmoColors.FillNeutral, RoundedCornerShape(10.dp)),
                    ) {
                        Image(painterResource(R.drawable.ic_edit), contentDescription = null)
                    }
                }
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = OrmoColors.ChevronWeak,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .size(24.dp),
                    )
                    Text(
                        stringResource(R.string.photo_add),
                        style = OrmoType.BodyMdSemibold,
                        color = OrmoColors.TextSecondary,
                    )
                    Text(
                        stringResource(R.string.photo_limit),
                        style = OrmoType.BodySmRegular,
                        color = OrmoColors.TextTertiary,
                    )
                }
            }
        }
        state.problem?.let {
            Text(
                stringResource(
                    when (it) {
                        PhotoProblem.TOO_BIG -> R.string.photo_too_big
                        PhotoProblem.UNSUPPORTED -> R.string.photo_unsupported
                        PhotoProblem.UNREADABLE -> R.string.photo_unreadable
                    },
                ),
                style = OrmoType.BodySmRegular,
                color = OrmoColors.TextDanger,
            )
        }
    }
}

/** 저장된 사진 파일을 칸에 꽉 채워 그린다. */
@Composable
fun PhotoImage(path: String, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier) }
}

/**
 * 사진 칸 오른쪽에 여는 메뉴와 카메라·앨범 실행. [panelPosition]은 메뉴를 그리는 상자의 화면 위치(px).
 * 시스템 카메라와 사진 선택기를 쓰므로 앱 권한을 요청하지 않는다.
 */
@Composable
fun PhotoMenuHost(state: MenuPhotoState, hasPhoto: Boolean, panelPosition: Offset, onNoCamera: () -> Unit) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) state.onPicked(uri, fromAlbum = true)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) state.onPicked(Uri.parse(uri), fromAlbum = false)
    }

    if (!state.menuOpen) return
    BackHandler { state.menuOpen = false }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { state.menuOpen = false } },
    )
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .offset {
                val slot = state.slotPosition - panelPosition
                // "사진" 라벨(32dp) 아래 칸의 위쪽에 맞추고, 칸 오른쪽에서 8dp 띄운다.
                IntOffset(
                    (slot.x + (SlotSize + 8.dp).toPx()).roundToInt(),
                    (slot.y + 32.dp.toPx()).roundToInt(),
                )
            }
            .width(240.dp)
            .shadow(8.dp, shape)
            .background(OrmoColors.BackgroundDefault, shape)
            .border(1.dp, OrmoColors.Border, shape)
            .padding(horizontal = 5.dp, vertical = 9.dp),
    ) {
        PhotoMenuItem(R.drawable.ic_camera, stringResource(R.string.photo_take)) {
            state.menuOpen = false
            val uri = cameraOutputUri(context)
            cameraUri = uri.toString()
            runCatching { camera.launch(uri) }.onFailure { onNoCamera() }
        }
        PhotoMenuItem(R.drawable.ic_image, stringResource(R.string.photo_album)) {
            state.menuOpen = false
            album.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (hasPhoto) {
            HorizontalDivider(color = OrmoColors.Border)
            PhotoMenuItem(R.drawable.ic_trash, stringResource(R.string.photo_remove), danger = true) {
                state.menuOpen = false
                state.onRemove()
            }
        }
    }
}

@Composable
private fun PhotoMenuItem(icon: Int, text: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (danger) OrmoColors.TextDanger else OrmoColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            style = OrmoType.BodyMdRegular,
            color = if (danger) OrmoColors.TextDanger else OrmoColors.TextPrimary,
        )
    }
}

/** 사진 맞추기: 끌어서 위치, 두 손가락으로 크기. 아래는 고객 앱 메뉴 카드 미리보기다. */
@Composable
fun CropDialog(state: MenuPhotoState, name: String, price: Int, description: String) {
    val source = state.cropping ?: return
    val image = remember(source) { source.asImageBitmap() }
    var window by remember(source) { mutableStateOf(CropWindow(source.width, source.height)) }

    DialogSurface(width = 560.dp, onDismiss = state::onCropDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.photo_crop_title), style = OrmoType.TitleBold, color = OrmoColors.TextStrong)
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(source) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val zoomed = window.zoomBy(zoom)
                            val perPixel = zoomed.side / size.width
                            window = zoomed.pan(-pan.x * perPixel, -pan.y * perPixel)
                        }
                    },
            ) {
                CroppedImage(image, window, Modifier.fillMaxSize())
                // 3x3 안내선
                Canvas(Modifier.fillMaxSize()) {
                    val line = 1.dp.toPx()
                    for (i in 1..2) {
                        val x = size.width * i / 3
                        val y = size.height * i / 3
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), line, alpha = 0.7f)
                        drawLine(Color.White, Offset(0f, y), Offset(size.width, y), line, alpha = 0.7f)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OrmoColors.Border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { stringResource(R.string.menu_name_hint) },
                        style = OrmoType.BodyLgBold,
                        color = OrmoColors.TextPrimary,
                        maxLines = 1,
                    )
                    Text(formatWon(price), style = OrmoType.BodyMdSemibold, color = OrmoColors.TextPrimary)
                    if (description.isNotBlank()) {
                        Text(description, style = OrmoType.BodySmRegular, color = OrmoColors.TextSecondary, maxLines = 2)
                    }
                }
                val shape = RoundedCornerShape(14.dp)
                CroppedImage(
                    image,
                    window,
                    Modifier
                        .size(108.dp)
                        .clip(shape)
                        .border(1.dp, OrmoColors.Border, shape),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = stringResource(R.string.photo_pick_again),
                    tone = ButtonTone.NEUTRAL_WEAK,
                    onClick = state::onPickAgain,
                    height = 56.dp,
                    radius = 16.dp,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = stringResource(R.string.photo_use),
                    tone = ButtonTone.BRAND,
                    onClick = { state.onCropped(window) },
                    height = 56.dp,
                    radius = 16.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CroppedImage(image: ImageBitmap, window: CropWindow, modifier: Modifier) {
    Canvas(modifier) {
        val side = window.side.roundToInt().coerceAtLeast(1)
        drawImage(
            image = image,
            srcOffset = IntOffset(window.left.roundToInt(), window.top.roundToInt()),
            srcSize = IntSize(side, side),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}
