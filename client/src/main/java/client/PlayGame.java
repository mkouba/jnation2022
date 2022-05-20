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
            Console.red("Type your name...");
            return 1;
        }

        UnicastProcessor<ExerciseResult> results = UnicastProcessor.create();

        JoinResult joinResult = game.join(JoinRequest.newBuilder().setName(args[0]).build()).await()
                .atMost(Duration.ofSeconds(10));

        String token = joinResult.getToken();

        logHeader(joinResult);

        game.play(results).subscribe().with(sm -> {
            Console.green(sm.getText());
        }, t -> Log.error(t.getMessage()));

        gameLoop(token, results);

        return 0;
    }

    private void logHeader(JoinResult r) {
        Console.cyan("=".repeat(40));
        Console.println("Joined the game as player %s", r.getPlayer());
        Console.println("The current exercise is: %s", r.getCurrentTask());
        Console.cyan("=".repeat(40));
    }

    private void gameLoop(String token, UnicastProcessor<ExerciseResult> results) {
        while (true) {
            String resultStr = "";
            try {
                resultStr = System.console().readLine().trim();
                long result = Long.parseLong(resultStr);
                results.onNext(ExerciseResult.newBuilder().setToken(token).setResponse(result).build());
            } catch (NumberFormatException e) {
                Console.red("%s is not a long value", resultStr);
            }
        }
    }
}
