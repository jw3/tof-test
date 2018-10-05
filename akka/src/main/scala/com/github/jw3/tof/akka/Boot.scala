package com.github.jw3.tof.akka

import akka.actor.ActorSystem
import com.github.jw3.tof.akka.VL53L1Device.{BootDevice, Configure}
import com.typesafe.scalalogging.LazyLogging
import pigpio.scaladsl.{PigpioLibrary, UserGpio}
import pigpio.vl53l1x.javadsl.Vl53l1xLibrary

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

object Boot extends App with LazyLogging {
  implicit val system: ActorSystem = ActorSystem("tof-example")
  implicit val lgpio: PigpioLibrary = PigpioLibrary.INSTANCE

  // initialize pigpio
  lgpio.gpioInitialise() match {
    case PigpioLibrary.PI_INIT_FAILED ⇒
      println("pigpio init failed")
      val f = system.terminate()
      println("terminating actor system")
      Await.ready(f, Duration.Inf)
      System.exit(1)

    case ver ⇒
      println(s"initialized pigpio v$ver")
  }

  println(s".:| Example Time of Flight w/ Akka |:.")

  // bring up the tof sensor
  val vl53l1 = Vl53l1xLibrary.INSTANCE
  val interruptPin = UserGpio(22)
  val shutoffPin = UserGpio(23)

  val ranger = system.actorOf(VL53L1Device.props(interruptPin, shutoffPin), "ranger")
  ranger ! BootDevice(VL53L1Device.DefaultAddr)
  ranger ! Configure(20 * 1000, 55)

  // bring up the stepper
  val stepperPins = Seq(4, 5, 6, 7).map(UserGpio)
  val stepper = system.actorOf(Stepper.props(Stepper.HalfStep, stepperPins))

  // sample
  import system.dispatcher
  system.scheduler.schedule(0.seconds, 1.second, ranger, VL53L1Device.Sample)
}
