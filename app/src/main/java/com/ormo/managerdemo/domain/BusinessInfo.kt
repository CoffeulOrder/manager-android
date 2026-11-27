package com.ormo.managerdemo.domain

/** 하단 고정 푸터에 표시하는 사업자 정보 (통신판매중개자 고지). */
data class BusinessInfo(
    val companyName: String,
    val representative: String,
    val businessRegistrationNumber: String,
    val mailOrderSalesNumber: String,
    val address: String,
    val customerCenterPhone: String,
    val email: String,
)

/**
 * ★ 사업자 정보는 여기 한 곳에서만 바꿉니다. ★
 * 아직 모르는 값은 "TODO: ..." 자리표시자로 두었으니, 실제 값으로 교체하세요. (값은 지어내지 않았습니다.)
 */
val ORMO_BUSINESS_INFO = BusinessInfo(
    companyName = "TODO: 상호명 입력",
    representative = "TODO: 대표자명 입력",
    businessRegistrationNumber = "TODO: 사업자등록번호 입력",
    mailOrderSalesNumber = "TODO: 통신판매업 신고번호 입력",
    address = "TODO: 사업장 주소 입력",
    customerCenterPhone = "TODO: 고객센터 전화번호 입력",
    email = "TODO: 이메일 입력",
)
