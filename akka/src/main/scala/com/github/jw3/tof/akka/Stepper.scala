package com.github.jw3.tof.akka

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import com.github.jw3.tof.akka.GpioPinGroup.{MemberLevel, MemberPinMode}
import com.github.jw3.tof.akka.Stepper.{NextStep, StartStepping, StepDirection, StepSize}
import pigpio.scaladsl.{Level, OutputPin, PigpioLibrary, UserGpio}

import scala.concurrent.duration.FiniteDuration

object Stepper {
  def props(size: StepSize, pins: Seq[UserGpio])(implicit lgpio: PigpioLibrary) =
    Props(new Stepper(size, pins))

  sealed trait StepSize {
    def steps: Seq[Int]
    def size: Int = steps.size
    def next(prev: Int, dir: StepDirection): Int = dir match {
      case Forward ⇒ if (prev < size - 1) prev + 1 else 0
      case Reverse ⇒ if (prev > 0) prev - 1 else size - 1
    }
  }
  object FullStep extends StepSize { val steps = Seq(8, 4, 2, 1) }
  object HalfStep extends StepSize { val steps = Seq(8, 12, 4, 6, 2, 3, 1, 9) }

  def levels(v: Int): Seq[Level] = Seq(Level(v & 1 << 3), Level(v & 1 << 2), Level(v & 1 << 1), Level(v & 1))

  sealed trait StepDirection
  object Forward extends StepDirection
  object Reverse extends StepDirection

  case class StartStepping(dir: StepDirection, delay: FiniteDuration)
  case object NextStep
}

class Stepper(size: StepSize, pins: Seq[UserGpio])(implicit lgpio: PigpioLibrary)
    extends Actor
    with Timers
    with ActorLogging {

  val gpio: ActorRef = context.actorOf(GpioPinGroup.props(pins: _*))
  gpio ! MemberPinMode(OutputPin, pins)

  def ready: Receive = {
    case StartStepping(dir, delay) ⇒
      levels(0).foreach(gpio ! _)
      context.become(stepping(size.next(0, dir), dir, delay))
  }

  def stepping(step: Int, dir: StepDirection, delay: FiniteDuration): Receive = {

    timers.startSingleTimer("step", NextStep, delay)

    {
      case NextStep ⇒
        levels(step).foreach(gpio ! _)
        context.become(stepping(size.next(step, dir), dir, delay))
    }
  }

  def levels(step: Int): Seq[MemberLevel] =
    pins.zip(Stepper.levels(size.steps(step))).map(t ⇒ MemberLevel(t._2, Seq(t._1)))

  def receive: Receive = ready
}
