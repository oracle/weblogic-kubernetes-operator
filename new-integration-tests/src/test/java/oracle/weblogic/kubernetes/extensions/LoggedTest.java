package oracle.weblogic.kubernetes.extensions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;

import java.util.logging.Logger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public interface LoggedTest {
    static final Logger logger = Logger.getLogger(LoggedTest.class.getName());

    @BeforeEach
    default void beforeEachTest(TestInfo testInfo) {
        logger.info(() -> String.format("About to execute [%s] in %s",
                testInfo.getDisplayName(),
                getMethodName(testInfo)));
    }

    @AfterEach
    default void afterEachTest(TestInfo testInfo) {
        logger.info(() -> String.format("Finished executing [%s] in %s",
                testInfo.getDisplayName(),
                getMethodName(testInfo)));
    }

    private String getMethodName(TestInfo testInfo) {
        String[] tempMethodName = testInfo.getTestMethod().get().toString().split(" ");
        return tempMethodName[tempMethodName.length - 1];
    }
}
