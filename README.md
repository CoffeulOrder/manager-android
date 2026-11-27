# ORMO 관리자앱 데모 (포트원 심사용)

대학교 안 카페 주문 앱 "ORMO"의 점주용 관리자앱 **간소화 데모**입니다. 주문 **수락 / 거절 / 환불**과
하단 **사업자 정보 고정 푸터**만 구현했고, 모든 데이터는 앱 메모리의 mock입니다. (서버·네트워크 없음)

- Kotlin · Jetpack Compose · Material 3 · 단일 Activity · ViewModel + StateFlow · 인메모리 Repository
- 태블릿 가로 고정, 라이트 테마. 디자인은 Figma `UIUX-Design` > `Design [사장님 웹]`(기준 1280x800)을 따르고,
  화면 크기가 다르면 같은 비율로 확대·축소합니다. 브랜드 색 `#298DFF`, 글꼴 Pretendard(`licenses/Pretendard-OFL.txt`)
- Figma처럼 보이도록 시스템 상태바·내비게이션 바를 숨깁니다.
- `minSdk 26` / `compileSdk 37` / `targetSdk 35` / JDK 17 (AGP 9.5.1, Kotlin 2.4.21, Gradle 9.9.1, Compose BOM 2026.09.00)

## 실행 방법

### Android Studio (권장)
1. 최신 안정 버전 Android Studio로 이 폴더(`manager-demo`)를 엽니다. (AGP 9.5 → Android Studio가 지원 버전이어야 합니다.)
2. Gradle Sync 후, 태블릿 가로 에뮬레이터(예: *Pixel Tablet*, 또는 Device Manager에서 *Tablet → 1194x834dp 근처* 프로파일)를 만들어 `app`을 실행합니다.
3. SDK Platform **37**과 Build-Tools **36.0.0**이 필요합니다(SDK Manager에서 설치, 보통 Studio가 안내합니다).

### 명령줄
```bash
./gradlew :app:assembleDebug     # APK 빌드
./gradlew :app:installDebug      # 연결된 에뮬레이터/기기에 설치
./gradlew :app:testDebugUnitTest # 단위 테스트
```
> `gradle/wrapper/gradle-wrapper.jar`와 `gradlew` 스크립트는 포함되어 있지 않을 수 있습니다.
> 그 경우 Android Studio로 열면 `gradle-wrapper.properties`(Gradle 9.9.1)를 보고 알아서 받아 쓰고,
> 명령줄을 쓰려면 Gradle이 설치된 곳에서 한 번 `gradle wrapper`를 실행하세요.

## 로그인
앱을 켜면 로그인 화면이 먼저 나옵니다. 서버가 없는 데모라 **아무 아이디·비밀번호나 통과**합니다(코드에 계정 값 없음).
디자인 QA용으로 화면을 보는 방법:

| 화면 | 방법 |
|---|---|
| 0.1.2 로그인 실패 | 아이디에 `fail` |
| 0.1.3 5번 실패(30초 잠금) | 아이디 `fail`로 5번 |
| 0.2.1 첫 로그인 · 비밀번호 바꾸기 | 앱을 켠 뒤 첫 로그인 |

로그인 상태는 앱 메모리에만 있어 앱을 다시 켜면 로그인부터 시작합니다(30일 유지는 백엔드 연동 때).

## 데모 시나리오
1. **주문 보드**: 접수 대기 | 제조중 | 픽업 대기 3열.
2. **수락 → 조리 완료 → 픽업 완료**: 카드 버튼으로 다음 열로 옮깁니다. `조리 완료`는 10초 안에 `되돌리기`가 됩니다.
3. **거절**: `거절` → 사유 4개 중 선택(품절이면 "오늘만 품절로 바꾸기" 체크) → `거절하기`.
4. **미수령**: 픽업 대기 20분이 지난 카드의 `정리하기`(환불 없음).
5. **환불**: `오늘 내역` → 픽업 완료·미수령 주문의 행 → `환불하기` → 사유 선택 → `N원 환불하기`. (전액, PIN 없음)
6. 접수 대기로 10분이 지나면 **자동 취소**(전액 환불)됩니다.
7. 하단의 `테스트 주문 추가`(Figma에 없는 데모 버튼)로 접수 대기 주문을 만듭니다.

## 알림
- **새 주문**: `새 주문이 N건 들어왔어요` 스낵바와 알림음(3초마다 반복). 수락·거절을 누르거나, `닫기`/왼쪽으로 밀면 멈춥니다.
  다른 탭에서는 `주문 보기`가 나옵니다. 앱을 켤 때 이미 있던 주문은 알리지 않으니 `테스트 주문 추가`로 확인하세요.
- **자동 취소 2분 전**: 남은 시간이 빨간색이 되고 알림음이 한 번 울립니다.
- **소리 꺼짐**: 미디어 음량이 0이거나 블루투스(A2DP)가 연결되면 안내와 `소리 키기`가 나옵니다.
- **처리 실패**: 결제 취소가 실패하면 `다시 시도`가 나옵니다. (Fake 게이트웨이는 항상 성공해 데모에서는 보이지 않습니다.)
- 스낵바는 왼쪽 아래에 온 순서대로 최대 3개까지 쌓입니다. 알림음은 기기 기본 알림음을 씁니다.

## 영업 상태
상단의 `영업 중` 버튼에서 바꿉니다. 새 주문은 영업 중일 때만 받습니다(`테스트 주문 추가`도 막힙니다).
- **잠시 중지**: 시간(10·20·30·60분·직접 다시 열기)과 사유를 고릅니다. 시간이 끝나면 자동으로 다시 열립니다.
- **마감**: 확인 뒤 마감합니다. 접수 대기가 있으면 한 번 더 묻고, `모두 취소하기`는 전액 환불 후 마감합니다.
- 잠시 중지·마감 안내 스낵바는 영업을 다시 시작할 때까지 떠 있습니다. 이미 받은 주문은 계속 처리할 수 있습니다.

## 메뉴 관리
`메뉴 관리` 탭에서 카테고리를 고르고 스위치로 품절/판매 중을 바꿉니다(바로 저장, 비밀번호 없음).
- 접수 대기 주문에 있는 메뉴를 품절로 바꾸면 `주문 보기` 안내가 나옵니다.
- 거절 창에서 "오늘만 품절로 바꾸기"를 고른 메뉴도 여기에 품절로 반영됩니다.
- `테스트 주문 추가`는 품절 메뉴를 빼고 만듭니다.
- `옵션` 탭에서는 옵션별 품절을 바꾸고 전체/판매중/품절로 거릅니다. 품절 옵션이 든 줄은 테스트 주문에서 빠집니다.
- **추가·수정·삭제·숨김**은 **마감**일 때만 열리고 2차 비밀번호를 묻습니다(메뉴 탭을 나가면 다시 묻습니다).
  - 메뉴 줄을 누르면 오른쪽에 상세가 열립니다: 이름·가격·설명·카테고리·옵션 연결·`고객에게 보이기`. 이름과 가격이 있어야 저장되고 이름은 겹칠 수 없습니다.
  - `메뉴 삭제`는 확인 후 바로 목록에서 빠지고 10초 동안 되돌릴 수 있습니다. 숨긴 메뉴는 카테고리 맨 아래 `숨긴 메뉴`에 모입니다.
  - 옵션 그룹은 새로 만들어 메뉴에 연결합니다. Figma에 화면이 없어 **만든 옵션 그룹의 수정·삭제는 없습니다**.
  - 품절·숨김·삭제한 메뉴(이름을 바꾼 메뉴의 옛 이름 포함)는 테스트 주문에서 빠집니다. 지난 주문·매출 기록은 그대로입니다.
- 아직 없는 것: 옵션 상세(메뉴마다 따로 품절), 메뉴 사진 올리기(Figma 검토용), 옵션 순서 바꾸기.
- 목록 초기값은 `data/MenuRepository.kt`의 `MockMenus`, `MockOptions`이며 앱을 다시 켜면 돌아갑니다.

## 매출과 2차 비밀번호
`매출` 탭에 들어갈 때와 메뉴를 추가·수정할 때 4자리 2차 비밀번호를 묻습니다. `설정`에서 바꿀 수 있습니다. 처음에는 직접 두 번 입력해 정합니다(코드에 정해진 값 없음).
- 5번째 틀릴 때부터 30초씩 기다립니다. 탭을 나갔다 들어오면 다시 묻습니다.
- 비밀번호는 앱 메모리에만 있어 **앱을 다시 켜면 사라집니다**. 실제 서비스에서는 서버가 저장·확인해야 합니다.
- 일간·주간·월간(그래프/달력)을 봅니다. **오늘**은 앱 안의 주문으로 계산해서 환불하면 바로 반영됩니다.
  **지난 날짜**는 `data/MockSales.kt`가 만드는 가짜 값입니다(일요일은 쉬는 날).

## 설정
- **영업시간**: 월~일·공휴일을 따로 정합니다. 시각은 숫자 4자리(24시간)로 치고, 여는 시각이 닫는 시각보다 빨라야 저장됩니다
  (자정을 넘기는 영업은 지원하지 않음). 고객 앱 안내용 값이라 시간이 돼도 자동으로 열거나 닫지 않습니다.
  목록의 요약 줄은 월~일 중 같은 시간이 가장 길게 이어지는 요일을 보여줍니다. 초기값은 Figma 예시이며 앱을 다시 켜면 돌아갑니다.
- **2차 비밀번호 바꾸기**: 지금 → 새 → 한 번 더. 아직 정한 적이 없으면 처음 정하기가 열립니다.
- **로그아웃**: 영업 중·잠시 중지에는 막고 마감을 안내합니다. 마감 뒤 확인하면 로그인 화면으로 갑니다.
- **비밀번호 바꾸기**는 Figma에 화면이 없어 "준비 중" 알림만 뜹니다. `알림음`은 `기본음` 하나라 고를 수 없습니다.

## 주문 상태 전이
상태 전이는 `domain/OrderTransitions.kt` 한 곳에 있습니다.
`접수 대기 → 제조중 | 거절 | 자동 취소 | 고객 취소`, `제조중 → 픽업 대기`,
`픽업 대기 → 픽업 완료 | 미수령`, `픽업 완료 | 미수령 → 환불`.

**아직 만들지 않은 것**: 옵션 그룹 수정·삭제, 옵션 상세, 메뉴 사진, 설정의 비밀번호 바꾸기, 영업 시작 때 오늘 내역 비우기,
고객 취소(서버가 있어야 발생), 메뉴를 골라 하는 부분 환불, 환불 메모 전송.

## 사업자 정보를 바꾸는 위치
[`app/src/main/java/com/ormo/managerdemo/domain/BusinessInfo.kt`](app/src/main/java/com/ormo/managerdemo/domain/BusinessInfo.kt)의
**`ORMO_BUSINESS_INFO`** 한 곳만 수정하면 됩니다. 지금은 `TODO: 상호명 입력` 같은 자리표시자입니다.
하단 푸터는 Figma에는 없지만 심사용으로 유지합니다. 항목 라벨(상호명, 대표자명 …)은 `res/values/strings.xml`의 `biz_*`에 있습니다.

## PaymentGateway를 실제 서버 연동으로 교체하는 위치
- 인터페이스: [`payment/PaymentGateway.kt`](app/src/main/java/com/ormo/managerdemo/payment/PaymentGateway.kt) — `suspend fun cancel(orderId, reason, amount): Result<Unit>`
- 현재 구현: [`payment/FakePaymentGateway.kt`](app/src/main/java/com/ormo/managerdemo/payment/FakePaymentGateway.kt) (즉시 성공)
- 교체 지점: [`ui/OrderViewModel.kt`](app/src/main/java/com/ormo/managerdemo/ui/OrderViewModel.kt)의 `Factory` 안
  `paymentGateway = FakePaymentGateway()` 한 줄을, **자사 BE의 결제 취소 엔드포인트를 호출하는 구현**으로 바꾸세요.
  BE가 포트원 결제 취소 API를 호출합니다.
- **포트원 API 시크릿/키는 앱에 넣지 않습니다.** 취소 API는 서버에서만 호출합니다.
- 거절/환불 모두 "결제 취소 성공 → 상태 변경" 순서로 처리하며, 취소가 실패하면 상태는 그대로 두고 스낵바로 알립니다.

## 로그인을 실제 서버 연동으로 교체하는 위치
- 인터페이스: [`auth/AuthGateway.kt`](app/src/main/java/com/ormo/managerdemo/auth/AuthGateway.kt) — `login`, `changePassword`, `logout`.
  `login`은 `Success(firstLogin)` / `Rejected` / `Locked(retryAfterSeconds)`를 돌려주고, 통신 오류는 `Result.failure`입니다.
- 교체 지점: [`MainActivity.kt`](app/src/main/java/com/ormo/managerdemo/MainActivity.kt)의 `FakeAuthGateway()` 한 줄.
- 네트워크를 붙이면 `AndroidManifest.xml`에 `INTERNET` 권한을 추가하세요. 토큰 저장과 30일 로그인 유지, 저장된 토큰으로
  바로 들어가기(`LoginViewModel`의 초기 단계)는 아직 없습니다.
- 틀린 횟수·잠금 시간은 서버가 셉니다. 비밀번호는 앱에 저장하지 않습니다.

## 데모 전용 코드 (나중에 제거)
- `auth/FakeAuthGateway.kt` 전체(`fail` 아이디 포함), `data/MockOrders.kt` 전체, `OrderViewModel.addTestOrder()`, 하단 푸터의 `테스트 주문 추가` 버튼

## 구조
```
domain/   Order, OrderStatus, RejectReason, OrderTransitions(전이 표), BusinessInfo, BusinessHours, Formatting(금액/경과 시간)
data/     OrderRepository(인메모리, 전이 검증), MockOrders
payment/  PaymentGateway, FakePaymentGateway
auth/     AuthGateway, FakeAuthGateway
ui/       LoginViewModel, LoginScreen, OrderViewModel, ManagerScreen, TopBar, BoardScreen, OrderCard, HistoryScreen,
          MenuScreen, MenuEditor, SalesScreen, SettingsScreen, SettingsViewModel, PinViewModel, PinDialog, OrderDialogs, StoreStatusMenu, Snackbars, SnackbarStack, DeviceSound, Components, BusinessFooter, theme/(Figma 색·글자 토큰)
```

## 테스트
`app/src/test` — 상태 전이 표(전수 검사), Repository 시각 기록, ViewModel 흐름(수락/거절/조리 완료 되돌리기/
자동 취소/미수령/환불/더블 탭/결제 취소 실패와 다시 시도/새 주문 알림/영업 상태/메뉴·옵션 품절·추가·수정·삭제), 로그인(첫 로그인·실패·잠금·로그아웃)과 비밀번호 규칙, 2차 비밀번호(입력·변경), 영업시간, 매출 계산, 스낵바 쌓기, 내역 필터·번호 검색, 금액("10,500원")·남은 시간 포맷.

> `gradle.properties`에 `-Dfile.encoding=UTF-8`을 넣지 마세요. Windows 사용자 폴더 이름이 한글이면
> 단위 테스트가 `ClassNotFoundException: GradleWorkerMain`으로 실패합니다.
