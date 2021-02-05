package com.atlassian.performance.tools.ssh.api

import org.junit.Assert
import org.junit.Test
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

class SshTest {

    @Test
    fun shouldDetachProcess() {
        SshContainer().useSsh { sshHost ->
            installPing(sshHost)

            val ping = sshHost.newConnection().use { ssh ->
                @Suppress("DEPRECATION") // tests public API
                ssh.startProcess("ping localhost")
            }
            sshHost.newConnection().use { ssh ->
                @Suppress("DEPRECATION") // tests public API
                ssh.stopProcess(ping)
            }
        }
    }

    @Test
    fun shouldNotWaitForBackground() {
        SshContainer().useSsh { sshHost ->
            val runMillis = measureTimeMillis {
                sshHost.runInBackground("sleep 8")
            }

            Assert.assertTrue(runMillis < 1000)
        }
    }

    @Test
    fun shouldGetBackgroundResults() {
        SshContainer().useSsh { sshHost ->
            installPing(sshHost)

            val ping = sshHost.runInBackground("ping localhost")
            Thread.sleep(2000)
            // meanwhile we can create and kill connections
            sshHost.newConnection().use { it.safeExecute("ls") }
            Thread.sleep(2000)
            val pingResult = ping.stop(Duration.ofMillis(20))

            Assert.assertTrue(pingResult.isSuccessful())
            Assert.assertTrue(pingResult.output.contains("localhost ping statistics"))
        }
    }

    @Test
    fun shouldTolerateEarlyFinish() {
        SshContainer().useSsh { sshHost ->
            installPing(sshHost)

            val fail = sshHost.runInBackground("nonexistent-command")
            val failResult = fail.stop(Duration.ofMillis(20))

            Assert.assertEquals(127, failResult.exitStatus)
        }
    }

    private fun installPing(sshHost: Ssh) {
        sshHost.newConnection().use { it.execute("apt-get update -qq && apt-get install iputils-ping -y") }
    }

    @Test
    fun shouldReadLongOutput() {
        val pool = Executors.newCachedThreadPool()
        (1..16)
            .map { Runnable { installJdk() } }
            .map { pool.submit(it) }
            .forEach { it.get() }
    }

    private fun installJdk() {
        SshContainer().useConnection { ssh ->
            ssh.execute("apt-get update -qq")
            ssh.execute("DEBIAN_FRONTEND=noninteractive apt-get install -qq openjdk-11-jdk", Duration.ofMinutes(5))
        }
    }
}
