package br.com.launchcontrol;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Inicia e encerra o worker de notificações junto com o ciclo de vida da aplicação. */
@WebListener
public class NotificationOutboxListener implements ServletContextListener {
    private ScheduledExecutorService executor;
    @Override public void contextInitialized(ServletContextEvent event) {
        NotificationDelivery.requeueConfigured();
        executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"launchcontrol-notification-outbox");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(NotificationDelivery::processPending,15,60,TimeUnit.SECONDS);
    }
    @Override public void contextDestroyed(ServletContextEvent event) {
        if(executor!=null){executor.shutdown();try{if(!executor.awaitTermination(5,TimeUnit.SECONDS))executor.shutdownNow();}catch(InterruptedException e){executor.shutdownNow();Thread.currentThread().interrupt();}}
    }
}
