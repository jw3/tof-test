package com.github.jw3.tof.akka

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.github.jw3.tof.akka.GpioPinGroup._
import pigpio.scaladsl.GpioPin.Listen
import pigpio.scaladsl._

import scala.util.{Failure, Success}


object GpioPinGroup {
  sealed trait ListeningMessage
  case class Listen() extends ListeningMessage
  case class Unlisten() extends ListeningMessage

  case class MemberQueryPinMode(gpio: UserGpio)
  case class MemberLevel(level: Level, gpio: Seq[UserGpio])
  case class MemberPinMode(mode: PinMode, gpio: Seq[UserGpio])
  case class MemberListen(gpio: Seq[UserGpio])
  case class MemberUnlisten(gpio: Seq[UserGpio])

  def apply(gpio: UserGpio*)(implicit lgpio: PigpioLibrary, sys: ActorSystem) = sys.actorOf(props(gpio: _*))
  def props(gpio: UserGpio*)(implicit lgpio: PigpioLibrary) = Props(new GpioPinGroup(gpio))
}


class GpioPinGroup(gpio: Seq[UserGpio])(implicit lgpio: PigpioLibrary) extends Actor with ActorLogging {
  import pigpio.scaladsl.PigpioLibrary.{INSTANCE ⇒ pigpio}

  log.debug("created [{}] actor", gpio)
  val alertbus: Map[UserGpio, ActorRef] = gpio.map(g ⇒ g → context.actorOf(GpioBus.props())).toMap
  val listener: Map[UserGpio, GpioAlertFunc] = gpio.map(g ⇒ g → new GpioAlertFunc(alertbus(g))).toMap


  def state(i: Set[UserGpio], o: Set[UserGpio]): Receive = {
    log.debug("[{}] in off state", gpio)

    {
      case MemberPinMode(InputPin, pins) ⇒
        val ii = pins.filter(gpio.contains).filterNot(i.contains).filterNot(o.contains).toSet
        ii.foreach { p ⇒
          pigpio.gpioSetAlertFunc(p.value, listener(p))
        }

        if(ii.nonEmpty)
          context.become(state(i ++ ii, o))

      case MemberPinMode(OutputPin, pins) ⇒
        val oo = pins.filter(gpio.contains).filterNot(i.contains).filterNot(o.contains).toSet
        if(oo.nonEmpty)
          context.become(state(i, o ++ oo))

      case MemberQueryPinMode(pin) =>
        if(i.contains(pin)) sender ! InputPin
        else if(o.contains(pin)) sender ! OutputPin
        else sender ! ClearPin

      case MemberListen(pins) ⇒
        pins.filter(gpio.contains).filterNot(o.contains).foreach(alertbus(_) ! Listen())

      case MemberUnlisten(pins) ⇒
        pins.filter(gpio.contains).filterNot(o.contains).foreach(alertbus(_) ! Listen())

      case MemberLevel(l, pins) ⇒
        pins.filter(gpio.contains).filterNot(i.contains).foreach{p ⇒
          DefaultDigitalIO.gpioWrite(p, l) match {
            case Success(r) =>
            case Failure(e) => throw e
          }
        }
    }
  }

  def receive: Receive = state(Set.empty, Set.empty)
}
