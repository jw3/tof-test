package com.github.jw3.tof.akka

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Timers}
import com.github.jw3.tof.akka.VL53L1Device.{BootDevice, Configure, _}
import pigpio.scaladsl.GpioPin.Listen
import pigpio.scaladsl._
import pigpio.vl53l1x.javadsl.Vl53l1xLibrary.{INSTANCE ⇒ vl53l1}
import pigpio.vl53l1x.javadsl.{I2C_HandleTypeDef, VL53L1_Dev_t, VL53L1_RangingMeasurementData_t}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object VL53L1Device {
  def props(int: UserGpio, xshut: UserGpio)(implicit lgpio: PigpioLibrary) = Props(new VL53L1Device(int, xshut))

  case class Configure(tb: Int, tp: Int)
  case object Sample
  case class Range(mm: Int)
  case class BootDevice(addr: Byte = 0x29)
  private case object DeviceBooted

  def Dev_t(addr: Int): VL53L1_Dev_t = {
    val dev = new VL53L1_Dev_t
    dev.I2cHandle = new I2C_HandleTypeDef.ByReference
    dev.I2cHandle.dummy = addr
    dev
  }

  val DefaultAddr: Byte = 0x29
}

class VL53L1Device(int: UserGpio, xshut: UserGpio)(implicit lgpio: PigpioLibrary)
    extends Actor
    with Stash
    with Timers
    with ActorLogging {

  val interruptPin: ActorRef = context.actorOf(GpioPin.props(int))
  val xshutPin: ActorRef = context.actorOf(GpioPin.props(xshut))

  xshutPin ! OutputPin
  interruptPin ! Listen()
  interruptPin ! InputPin

  def suspended: Receive = {
    xshutPin ! Low

    {
      case BootDevice(DefaultAddr) ⇒
        xshutPin ! High
        Thread.sleep(100) // no

        val i2c = lgpio.i2cOpen(1, DefaultAddr, 0)
        context.become(booting(Dev_t(i2c)))

      case BootDevice(addr) ⇒
        xshutPin ! High
        Thread.sleep(100) // no

        {
          val i2c = lgpio.i2cOpen(1, DefaultAddr, 0)
          val addr2x = addr + addr
          vl53l1.VL53L1_SetDeviceAddress(Dev_t(i2c), addr2x.byteValue())
          log.info("set address to {}", addr.toHexString)
          lgpio.i2cClose(i2c)
        }

        val i2c = lgpio.i2cOpen(1, addr, 0)
        context.become(booting(Dev_t(i2c)))
    }
  }

  def booting(dev: VL53L1_Dev_t): Receive = {
    import context.dispatcher

    log.info("waiting on device to boot")
    Future {
      vl53l1.VL53L1_WaitDeviceBooted(dev)
      context.self ! DeviceBooted
    }

    {
      case DeviceBooted ⇒
        unstashAll()
        log.info("device has booted")
        context.become(booted(dev))

      case _ ⇒ stash()
    }
  }

  def booted(dev: VL53L1_Dev_t): Receive = {
    case Configure(tb, tp) ⇒
      log.info("configuring device at {}:{}", tb, tp)

      println(vl53l1.VL53L1_DataInit(dev))
      println(vl53l1.VL53L1_StaticInit(dev))
      println(vl53l1.VL53L1_SetPresetMode(dev, 4)) // VL53L1_PRESETMODE_LITE_RANGING
      println(vl53l1.VL53L1_SetDistanceMode(dev, 1)) // VL53L1_DISTANCEMODE_SHORT
      println(vl53l1.VL53L1_SetMeasurementTimingBudgetMicroSeconds(dev, tb))
      println(vl53l1.VL53L1_SetInterMeasurementPeriodMilliSeconds(dev, tp))
      println(vl53l1.VL53L1_StartMeasurement(dev))

      context.become(configured(dev))
  }

  def configured(dev: VL53L1_Dev_t): Receive = {
    log.info("ready to measure")

    {
      case Sample ⇒
        log.debug("starting measurement")
        vl53l1.VL53L1_ClearInterruptAndStartMeasurement(dev)

        if (0 == vl53l1.VL53L1_WaitMeasurementDataReady(dev)) {
          val d = new VL53L1_RangingMeasurementData_t
          if (0 == vl53l1.VL53L1_GetRangingMeasurementData(dev, d))
            log.info("Range {} mm", d.RangeMilliMeter)
          else
            log.warning("measurement failed")
        }

        timers.startSingleTimer("sample", Sample, 1.second)
    }
  }

  def receive: Receive = suspended
}
