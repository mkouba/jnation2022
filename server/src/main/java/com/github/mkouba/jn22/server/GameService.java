package com.github.mkouba.jn22.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.event.Observes;

import com.github.mkouba.jn22.ExerciseResult;
import com.github.mkouba.jn22.Game;
import com.github.mkouba.jn22.JoinRequest;
import com.github.mkouba.jn22.JoinResult;
import com.github.mkouba.jn22.ServerMessage;

import io.quarkus.grpc.GrpcService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

@GrpcService
public class GameService implements Game {

    volatile Exercise exercise;

    final Map<String, String> players = new ConcurrentHashMap<>();
    final AtomicInteger counter = new AtomicInteger(0);

    final BroadcastProcessor<ServerMessage> serverMessages = BroadcastProcessor.create();

    void startup(@Observes StartupEvent event) {
        exercise = nextExercise();
    }

    @Override
    public Uni<JoinResult> join(JoinRequest request) {
        String player = request.getName() + counter.incrementAndGet();
        String token = UUID.randomUUID().toString();
        players.put(token, player);
        serverMessage("%s joined the game!", player);
        return Uni.createFrom()
                .item(JoinResult.newBuilder().setPlayer(player).setToken(token).setCurrentTask(exercise.task).build());
    }

    @Override
    public Multi<ServerMessage> play(Multi<ExerciseResult> request) {
        request.subscribe().with(r -> {
            String player = players.get(r.getToken());
            if (player == null) {
                Log.info("Unregistered player token: " + r.getToken());
            } else {
                if (r.getResponse() == exercise.expectedResult) {
                    if (exercise.tryWin(player)) {
                        serverMessage("Player %s won!", player);
                        exercise = nextExercise();
                    } else {
                        serverMessage("Player %s was too late...", player);
                    }
                } else {
                    serverMessage("Player %s sent a wrong response...", player);
                }
            }
        }, t -> Log.error(t.getMessage()));
        return serverMessages;
    }

    private Exercise nextExercise() {
        int a = ThreadLocalRandom.current().nextInt(10);
        int b = ThreadLocalRandom.current().nextInt(10);
        Exercise exercise = new Exercise(a + " + " + b, a + b);
        serverMessage("A new exercise is here: " + exercise.task);
        return exercise;
    }

    private void serverMessage(String text, Object... args) {
        String message = String.format(text, args);
        Log.info(message);
        serverMessages.onNext(ServerMessage.newBuilder().setText(message).build());
    }

    static class Exercise {

        final String task;
        final long expectedResult;
        volatile String winner;

        Exercise(String text, long result) {
            this.task = text;
            this.expectedResult = result;
        }

        boolean tryWin(String player) {
            if (winner == null) {
                synchronized (this) {
                    if (winner == null) {
                        winner = player;
                        return true;
                    }
                }
            }
            return false;
        }

    }

}
