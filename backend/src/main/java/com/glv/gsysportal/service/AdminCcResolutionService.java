package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 7-C3 3章's Admin CC Rule: From=ログインユーザー / To=Supplier Contact
 * Master / CC=ADMIN. WHICH ADMIN(s) get CC'd when there are several is
 * explicit CUSTOMER REVIEW (7-C3 3章/18章 - candidates listed: 全Active
 * ADMIN / Order担当ADMIN / Primary ADMIN / Supplier・Region担当ADMIN). This
 * Phase implements the simplest, safest default - 全Active ADMIN (every
 * enabled ADMIN account's email) - so Preview always has a definite,
 * visualizable answer, WITHOUT pretending the choice is final. A future
 * Phase may replace this method's body once the CUSTOMER REVIEW is resolved;
 * nothing else in this Phase depends on which specific rule wins.
 */
@Service
public class AdminCcResolutionService {

    private final PortalUserRepository portalUserRepository;

    public AdminCcResolutionService(PortalUserRepository portalUserRepository) {
        this.portalUserRepository = portalUserRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<String> resolveAdminCcEmails() {
        return portalUserRepository.findAllByRoleAndEnabledTrue(PortalUser.ROLE_ADMIN).stream()
                .map(PortalUser::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .toList();
    }
}
