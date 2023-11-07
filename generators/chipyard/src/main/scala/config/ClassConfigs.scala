package chipyard

import org.chipsalliance.cde.config.Config
import freechips.rocketchip.tilelink.TLBundleParameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MBUS, SBUS}
import testchipip.{OBUS, SerialTLKey}

class WithRoboSerialTLParams extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(p => p.copy(
    bundleParams = TLBundleParameters(
      addressBits=35, dataBits=64,
      sourceBits=4, sinkBits=5, sizeBits=4,
      echoFields=Nil, requestFields=Nil, responseFields=Nil,
      hasBCE=false
    )
  ))
})

class DigitalChipBringupHostConfig extends Config(
  //=============================
  // Set up TestHarness for standalone-sim
  //=============================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++  // Generate absolute frequencies
  new chipyard.harness.WithSerialTLTiedOff ++                       // when doing standalone sim, tie off the serial-tl port
  new chipyard.harness.WithSimTSIToUARTTSI ++                       // Attach SimTSI-over-UART to the UART-TSI port
  new chipyard.iobinders.WithSerialTLPunchthrough ++                // Don't generate IOCells for the serial TL (this design maps to FPGA)

  //=============================
  // Setup the SerialTL side on the bringup device
  //=============================
  new testchipip.WithSerialTLWidth(1) ++                                       // match width with the chip
  new testchipip.WithSerialTLMem(base = 0x0, size = 0x80000000L,               // accessible memory of the chip that doesn't come from the tethered host
                                 idBits = 4, isMainMemory = false) ++          // This assumes off-chip mem starts at 0x8000_0000
  new testchipip.WithSerialTLClockDirection(provideClockFreqMHz = None) ++     // DUT provides clock

  //============================
  // Setup bus topology on the bringup system
  //============================
  new testchipip.WithOffchipBusClient(SBUS,                                    // offchip bus hangs off the SBUS
    blockRange = AddressSet.misaligned(0x80000000L, (BigInt(1) << 30) * 4)) ++ // offchip bus should not see the main memory of the testchip, since that can be accessed directly
  new testchipip.WithOffchipBus ++                                             // offchip bus

  //=============================
  // Set up memory on the bringup system
  //=============================
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++         // match what the chip believes the max size should be

  //=============================
  // Generate the TSI-over-UART side of the bringup system
  //=============================
  new testchipip.WithUARTTSIClient(initBaudRate = BigInt(921600)) ++           // nonstandard baud rate to improve performance

  //=============================
  // Set up clocks of the bringup system
  //=============================
  new chipyard.clocking.WithPassthroughClockGenerator ++ // pass all the clocks through, since this isn't a chip
  new chipyard.config.WithFrontBusFrequency(50.0) ++     // run all buses of this system at 50 MHz
  new chipyard.config.WithMemoryBusFrequency(50.0) ++
  new chipyard.config.WithPeripheryBusFrequency(50.0) ++

  // Base is the no-cores config
  new chipyard.NoCoresConfig)

class RoboBringupHostConfig extends Config(
  new WithRoboSerialTLParams() ++
  new DigitalChipBringupHostConfig())
