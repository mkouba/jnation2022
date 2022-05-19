package client;

import javax.inject.Inject;

import com.github.mkouba.jn22.ExerciseResult;
import com.github.mkouba.jn22.Game;
import com.github.mkouba.jn22.JoinRequest;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Vertx;

@QuarkusMain
public class PlayGame implements QuarkusApplication {

    @GrpcClient
    Game game;
    
    @Inject
    Vertx vertx;

    @Override
    public int run(String... args) throws Exception {
        if (args.length == 0) {
            System.out.println("Type your name...");
            return 1;
        }

        game.join(JoinRequest.newBuilder().setName(args[0]).build()).subscribe().with(r -> {

            String token = r.getToken();

            System.out.println("=".repeat(30));
            System.out.println("Joined the game as player " + r.getPlayer());
            System.out.println("The current exercise is: " + r.getCurrentTask());
            System.out.println("=".repeat(30));
            
            Multi<ExerciseResult> multi = Multi.createFrom().emitter(em -> {
                
                while (true) {
                    String resultStr = "";
                    try {
                        resultStr = System.console().readLine().trim();
                        long result = Long.parseLong(resultStr);
                        em.emit((ExerciseResult.newBuilder().setToken(token).setResponse(result).build()));
                    } catch (NumberFormatException e) {
                        System.out.println(resultStr + " is not a long value");
                    }
                }    
            });            

            game.play(multi).subscribe().with(sm -> {
                System.out.println(sm.getText());
            });
            
        });

        Quarkus.waitForExit();
        return 0;
    }
    
}