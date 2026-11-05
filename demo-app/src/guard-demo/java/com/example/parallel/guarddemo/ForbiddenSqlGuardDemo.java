package com.example.parallel.guarddemo;

import com.example.parallel.it.AbstractJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * NEGATIVE CONTROL (expected: PT12). A bulk JPQL delete is not a forbidden *method* call, so only the
 * runtime SQL guard (Hibernate StatementInspector installed by the library) can stop it.
 */
class ForbiddenSqlGuardDemo extends AbstractJpaTest {

    @Autowired EntityManager em;

    @Test
    void wipesTheWholeTable() {
        em.createQuery("delete from Account").executeUpdate();
    }
}
