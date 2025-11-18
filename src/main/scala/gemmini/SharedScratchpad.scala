package gemmini

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._


case class SharedScratchpadConfig (
  // Enable a shared scratchpad in local accelerator.
  val enable: Boolean = false,
  // Base address for the entire address space.
  val global_base_addr: BigInt = BigInt("F0000000", 16),
  // Size in a local accelerator.
  val local_size_bytes: Int = 256 * 1024,
  // Number of banks.
  val local_banks: Int = 1,
  // Interleaved size of banks.
  val local_bank_interleaved_bytes: Int = 64,
  // Beat size of banks.
  val local_bank_beat_bytes: Int = 64,
) {
  require(local_size_bytes > 0 && isPow2(local_size_bytes))
  require(local_banks > 0 && isPow2(local_banks))
  require(local_bank_beat_bytes > 0 && isPow2(local_bank_beat_bytes))

  require(global_base_addr % (local_banks * local_bank_interleaved_bytes) == 0)
  require(local_size_bytes >= (local_banks * local_bank_interleaved_bytes))
  require(local_bank_interleaved_bytes >= local_bank_beat_bytes)

  val local_addr_mask: BigInt = BigInt(local_size_bytes) - 1

  val local_bank_addr_mask: BigInt = 
    local_addr_mask - (local_banks - 1) * local_bank_interleaved_bytes

  def local_base_addr(spad_id: Int): BigInt = {
    global_base_addr + local_size_bytes * spad_id
  }

  def local_bank_base_addr(spad_id: Int, bank_id: Int): BigInt = {
    local_base_addr(spad_id) + local_bank_interleaved_bytes * bank_id
  }

  def local_bank_addr_sets(spad_id: Int): Seq[AddressSet] = {
    (0 until local_banks).map { bank_id => {
      AddressSet(local_bank_base_addr(spad_id, bank_id), local_bank_addr_mask)
    }}
  }
}


class SharedScratchpad[T <: Data, U <: Data, V <: Data] (
  val config: GemminiArrayConfig[T, U, V]
) (
  implicit p: Parameters
) extends LazyModule {

  import config.{gemmini_id, dma_maxbytes, dma_buswidth}
  import config.shared_scratchpad_config._
  
  require(local_bank_interleaved_bytes >= dma_maxbytes)
  require(local_bank_beat_bytes <= dma_maxbytes)

  // Connected to bus for remote access
  val global_node = TLIdentityNode()
  // Connected to local DMA for local access
  val local_node = TLIdentityNode()
  // For TLRAM instantiation and TLFilter
  val bank_addr_sets: Seq[AddressSet] = local_bank_addr_sets(gemmini_id)

  // Locak crossbar
  private val bank_xbar = TLXbar()
  bank_xbar := TLBuffer() := global_node 
  bank_xbar := TLBuffer() := local_node

  // Device description for DTS
  private val memDevice = new MemoryDevice()

  // Instantiate one TLRAM per bank
  (0 until local_banks).foreach { bank_id => {
    val bank = LazyModule(new TLRAM(
      address     = bank_addr_sets(bank_id),
      beatBytes   = local_bank_beat_bytes,
      devOverride = Some(memDevice),
      cacheable   = false,
      executable  = false,
      atomics     = false,
    ))
    bank.suggestName(s"Gemmini${gemmini_id}-SharedScratchpadBank${bank_id}")
    bank.node := TLFragmenter(local_bank_beat_bytes, dma_maxbytes) := 
                 TLBuffer(4) := TLWidthWidget(dma_buswidth / 8) := bank_xbar
  }}

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {})
  }
}