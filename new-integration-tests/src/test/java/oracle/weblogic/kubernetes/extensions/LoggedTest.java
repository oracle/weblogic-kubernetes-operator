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
        String[] tempMethodName = testInfo.getTestMethod().get().toString().split(" ");
        String methodName = tempMethodName[tempMethodName.length - 1];
        logger.info(() -> String.format("About to execute [%s] [%s]",
                testInfo.getDisplayName(),
                methodName));
    }

    @AfterEach
    default void afterEachTest(TestInfo testInfo) {
        String[] tempMethodName = testInfo.getTestMethod().get().toString().split(" ");
        String methodName = tempMethodName[tempMethodName.length - 1];
        logger.info(() -> String.format("Finished executing [%s] [%s]",
                testInfo.getDisplayName(),
                methodName));
    }
}
