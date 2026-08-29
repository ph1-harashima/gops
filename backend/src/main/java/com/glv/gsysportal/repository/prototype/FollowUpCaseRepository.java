package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.FollowUpCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowUpCaseRepository extends JpaRepository<FollowUpCase, Long> {

    List<FollowUpCase> findByPortalOrderIdOrderByCreatedAtAsc(Long portalOrderId);

    long countByStatusIn(List<String> statuses);
}
