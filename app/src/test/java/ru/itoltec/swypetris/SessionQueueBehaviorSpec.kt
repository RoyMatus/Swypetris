package ru.itoltec.swypetris

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/** Session restoration must preserve the next pieces without drawing from a new bag. */
class SessionQueueBehaviorSpec : BehaviorSpec({
    Given("an unfinished game with pieces left in its seven-bag") {
        val original = GameEngine(Random(42))
        val state = original.newGame()
        val remaining = original.remainingBag()

        When("the bag is restored in another engine") {
            val restored = GameEngine(Random(99))
            restored.restoreBag(remaining)

            Then("the saved queue remains in order through every remaining draw") {
                var before = state
                var after = state
                repeat(remaining.size) {
                    before = original.apply(before.copy(board = List(20) { List(10) { null } }), GameCommand.HARD_DROP)
                    after = restored.apply(after.copy(board = List(20) { List(10) { null } }), GameCommand.HARD_DROP)
                    after.active.type shouldBe before.active.type
                    after.next shouldBe before.next
                }
                restored.remainingBag() shouldBe emptyList()
            }
        }
    }

    Given("a corrupt saved seven-bag") {
        When("it contains the same piece twice") {
            Then("restoration rejects it before changing the queue") {
                val engine = GameEngine(Random(42))
                engine.newGame()
                val remaining = engine.remainingBag()
                try {
                    engine.restoreBag(listOf(Tetromino.I, Tetromino.I))
                    error("A duplicate bag entry was accepted")
                } catch (_: IllegalArgumentException) {
                    engine.remainingBag() shouldBe remaining
                }
            }
        }
    }
})
