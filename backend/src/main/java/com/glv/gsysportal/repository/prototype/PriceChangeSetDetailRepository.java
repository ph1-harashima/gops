package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.PriceChangeSetDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceChangeSetDetailRepository extends JpaRepository<PriceChangeSetDetail, Long> {

    Optional<PriceChangeSetDetail> findByPriceChangeSetIdAndItemCd(Long priceChangeSetId, String itemCd);
}
