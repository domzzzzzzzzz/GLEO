package com.fbcorp.gleo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class GleoApplication {
    private static final Logger log = LoggerFactory.getLogger(GleoApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(GleoApplication.class, args);

        // Register a simple shutdown hook to attempt graceful shutdown of executors
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered - attempting graceful shutdown...");
            try {
                // Try to shutdown any ExecutorService beans gracefully
                Map<String, ExecutorService> executors = ctx.getBeansOfType(ExecutorService.class);
                for (Map.Entry<String, ExecutorService> e : executors.entrySet()) {
                    ExecutorService service = e.getValue();
                    try {
                        log.info("Shutting down executor bean: {}", e.getKey());
                        service.shutdown();
                        if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                            log.warn("Executor {} did not terminate in time, forcing shutdown", e.getKey());
                            service.shutdownNow();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while shutting down executor {}", e.getKey(), ie);
                    } catch (Exception ex) {
                        log.warn("Error while shutting down executor {}", e.getKey(), ex);
                    }
                }

                if (ctx.isActive()) {
                    log.info("Closing Spring context");
                    ctx.close();
                }
            } catch (Exception ex) {
                log.error("Exception in shutdown hook", ex);
            }
        }, "gleo-shutdown-hook"));
    }
}
