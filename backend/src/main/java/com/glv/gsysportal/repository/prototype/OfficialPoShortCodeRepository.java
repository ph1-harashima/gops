package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.OfficialPoShortCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficialPoShortCodeRepository extends JpaRepository<OfficialPoShortCode, Long> {

    List<OfficialPoShortCode> findAllByOrderByCodeTypeAscBusinessCodeAsc();

    Optional<OfficialPoShortCode> findFirstByCodeTypeAndBusinessCodeAndActiveTrue(String codeType, String businessCode);

    boolean existsByCodeTypeAndBusinessCodeAndActiveTrue(String codeType, String businessCode);
}
