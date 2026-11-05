package com.example.parallel.it;

import com.hittech.paralleltest.annotation.SharedContextBase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/** Base of all JPA slice tests: one shared slice context; every test method runs in a rolled-back transaction. */
@SharedContextBase
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractJpaTest {
}
