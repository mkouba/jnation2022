package client;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.github.mkouba.jn22.ExerciseResult;
import com.github.mkouba.jn22.Game;
import com.github.mkouba.jn22.JoinRequest;

import com.github.mkouba.jn22.JoinResult;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.core.Vertx;
import org.jboss.logging.Logger;

@QuarkusMain
public class PlayGame implements QuarkusApplication {

    @GrpcClient
    Game game;
    
    @Inject
    Vertx vertx;

    volatile boolean shutdown;

    void shutdown(@Observes ShutdownEvent event) {
        shutdown = true;
    }

    @Override
    public int run(String... args) {
        if (args.length == 0) {
            System.out.println("Type your name...");
            return 1;
        }

        game.join(JoinRequest.newBuilder().setName(args[0]).build()).subscribe().with(r -> {

            String token = r.getToken();
            logHeader(r);
            Multi<ExerciseResult> multi = Multi.createFrom().<ExerciseResult>emitter(
                    em -> vertx.executeBlocking(promise -> gameLoop(token, em))
            ).onFailure().invoke(error ->
                Log.log(org.jboss.logging.Logger.Level.ERROR, error)
            );

            game.play(multi).subscribe().with(sm -> {
                System.out.println(sm.getText());
            }, th -> {
                if (!shutdown) {
                    Log.log(Logger.Level.ERROR, "Failure on message consumption", th);
                }
            });
            
        });

        Quarkus.waitForExit();
        return 0;
    }

    private void logHeader(JoinResult r) {
        System.out.println("=".repeat(30));
        System.out.println("Joined the game as player " + r.getPlayer());
        System.out.println("The current exercise is: " + r.getCurrentTask());
        System.out.println("=".repeat(30));
    }

    private void gameLoop(String token, MultiEmitter<? super ExerciseResult> em) {
        while (!shutdown) {
            String resultStr = "";
            try {
                resultStr = System.console().readLine().trim();
                long result = Long.parseLong(resultStr);
                em.emit((ExerciseResult.newBuilder().setToken(token).setResponse(result).build()));
            } catch (NumberFormatException e) {
                System.out.println(resultStr + " is not a long value");
            }
        }
    }

}
