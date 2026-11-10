package id.eduduel.app.domain

import id.eduduel.app.TestBank
import id.eduduel.app.domain.game.AsteroidEngine
import id.eduduel.app.domain.game.AsteroidSpec
import id.eduduel.app.domain.game.AsteroidSubmission
import id.eduduel.app.domain.game.GameEngines
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.GameSubmit
import id.eduduel.app.net.protocol.MessageCodec
import id.eduduel.app.net.protocol.PROTOCOL_VERSION
import org.junit.Assert.*
import org.junit.Test

class AsteroidCollisionTest {
    private val question = TestBank.bank.questions.first { it.game?.spec is AsteroidSpec }
    private val spec = question.game!!.spec as AsteroidSpec
    private val rock = AsteroidEngine.Spawn(0, 0, 0.5, 0.0, 1.0)

    private fun timeAt(y: Double) = (y + 0.08) / spec.fallSpeed

    @Test fun overlapAndSweptPassLoseLife_butNearMissAndNotYetSpawnedDoNot() {
        assertTrue(AsteroidEngine.collidesWithShip(spec, rock, timeAt(0.9), timeAt(0.9), 0.5, 0.5))
        assertTrue(AsteroidEngine.collidesWithShip(spec, rock, timeAt(0.6), timeAt(1.2), 0.5, 0.5))
        assertFalse(AsteroidEngine.collidesWithShip(spec, rock, timeAt(0.6), timeAt(1.2), 0.85, 0.85))
        assertFalse(AsteroidEngine.collidesWithShip(spec, rock.copy(spawnAt = 100.0), 0.0, 1.0, 0.5, 0.5))
        assertFalse(AsteroidEngine.collidesWithShip(spec, rock, timeAt(1.2), timeAt(1.5), 0.5, 0.5))
    }

    @Test fun movingShipCannotTunnelThroughAnAsteroid() {
        val t = timeAt(0.9)
        assertTrue(AsteroidEngine.collidesWithShip(spec, rock, t, t + 0.01, 0.1, 0.9))
    }

    @Test fun roundedCornerDoesNotCountAsContact() {
        val r = AsteroidEngine.ASTEROID_RADIUS
        val x = 0.5 + AsteroidEngine.SHIP_WIDTH / 2 + r * 0.9
        val y = AsteroidEngine.SHIP_Y + (AsteroidEngine.SHIP_HEIGHT / 2 + r * 0.9) * AsteroidEngine.ARENA_RATIO
        val t = timeAt(y)
        assertFalse(AsteroidEngine.collidesWithShip(spec, rock.copy(x = x), t, t, 0.5, 0.5))
    }

    @Test fun collisionsAndWrongShotsShareTheSameLifeBudget_andHostReplaysOrder() {
        val answer = spec.answerTokens.indices.toList()
        val collision = AsteroidEngine.COLLISION
        val okay = AsteroidSubmission(List(spec.maxWrong) { collision } + answer)
        assertTrue(GameEngines.isWellFormed(spec, okay))
        assertTrue(GameEngines.evaluate(spec, okay).success)
        val fatal = AsteroidSubmission(List(spec.maxWrong + 1) { collision } + answer)
        assertFalse(GameEngines.evaluate(spec, fatal).success)
        val combined = AsteroidSubmission(List(spec.maxWrong) { spec.answerTokens.size } + collision + answer)
        assertFalse(GameEngines.evaluate(spec, combined).success)
        assertEquals(spec.maxWrong + 1, GameEngines.evaluate(spec, combined).mistakes)
        assertFalse(GameEngines.isWellFormed(spec, AsteroidSubmission(listOf(-2))))
    }

    @Test fun collisionPenaltySurvivesNetworkEncoding() {
        val event = Envelope(PROTOCOL_VERSION, 1, "event", "match", 0,
            GameSubmit(question.id, 1000, AsteroidSubmission(listOf(AsteroidEngine.COLLISION, 0))))
        val decoded = MessageCodec.decode(MessageCodec.encode(event).drop(4).toByteArray()) as id.eduduel.app.net.protocol.DecodeResult.Ok
        assertEquals(event, decoded.envelope)
    }
}
