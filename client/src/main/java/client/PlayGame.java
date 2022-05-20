package client;

import java.time.Duration;

import javax.inject.Inject;

import com.github.mkouba.jn22.ExerciseResult;
import com.github.mkouba.jn22.Game;
import com.github.mkouba.jn22.JoinRequest;
import com.github.mkouba.jn22.JoinResult;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.vertx.core.Vertx;

@QuarkusMain
public class PlayGame implements QuarkusApplication {

    @GrpcClient
    Game game;

    @Inject
    Vertx vertx;

    @Override
    public int run(String... args) {
        if (args.length == 0) {
            System.out.println("Type your name...");
            return 1;
        }

        UnicastProcessor<ExerciseResult> results = UnicastProcessor.create();

        JoinResult joinResult = game.join(JoinRequest.newBuilder().setName(args[0]).build()).await()
                .atMost(Duration.ofSeconds(10));

        String token = joinResult.getToken();
        
        logHeader(joinResult);

        game.play(results).subscribe().with(sm -> {
            System.out.println(sm.getText());
        }, t -> Log.error(t.getMessage()));

        gameLoop(token, results);

        return 0;
    }

    private void logHeader(JoinResult r) {
        System.out.println("=".repeat(30));
        System.out.println("Joined the game as player " + r.getPlayer());
        System.out.println("The current exercise is: " + r.getCurrentTask());
        System.out.println("=".repeat(30));
    }

    private void gameLoop(String token, UnicastProcessor<ExerciseResult> results) {
        while (true) {
            String resultStr = "";
            try {
                resultStr = System.console().readLine().trim();
                long result = Long.parseLong(resultStr);
                results.onNext(ExerciseResult.newBuilder().setToken(token).setResponse(result).build());
            } catch (NumberFormatException e) {
                System.out.println(resultStr + " is not a long value");
            }
        }
    }
}
