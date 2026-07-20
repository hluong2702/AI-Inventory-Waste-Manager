package vn.inventoryai.staff;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@Configuration
class InvitationEmailWorkerConfiguration {
    @Bean(name = "invitationEmailTaskScheduler", destroyMethod = "shutdown")
    ThreadPoolTaskScheduler invitationEmailTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("invitation-email-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(failure -> log.error(
                "Invitation email worker failed unexpectedly; errorType={}",
                failure.getClass().getSimpleName()
        ));
        return scheduler;
    }
}
