package com.dochibot.domain.entity

import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.util.UUID

/**
 * Spring Data에서 UUID 기반 엔티티의 INSERT/UPDATE 판단을 통일한다.
 *
 * Spring Data JDBC/R2DBC는 기본적으로 id의 null 여부로 신규 여부를 판단하기 때문에,
 * 애플리케이션에서 UUID를 미리 생성하는 경우에는 Persistable.isNew()를 통해 명시적으로 INSERT를 유도한다.
 */
abstract class BasePersistableUuidEntity : Persistable<UUID> {
    @Transient
    private var isNewEntity: Boolean = false

    override fun isNew(): Boolean = isNewEntity

    internal fun markAsNew() {
        isNewEntity = true
    }
}
