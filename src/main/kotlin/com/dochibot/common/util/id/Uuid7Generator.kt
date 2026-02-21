package com.dochibot.common.util.id

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * UUIDv7 생성 유틸리티.
 *
 * 타임스탬프 기반 정렬 가능한 UUID를 생성합니다.
 * - 타임스탬프: 48 bits
 * - 버전: 7
 * - 유니크: 랜덤
 */
object Uuid7Generator {

    /**
     * UUIDv7 생성.
     * @return 정렬 가능한 타임스탬프 기반 UUID
     */
    fun create(): UUID = UuidCreator.getTimeOrdered()
}
