 // import spatial._
 // import org.virtualized._

 // object Video extends SpatialApp {
 //   import IR._

 //   override val target = targets.DE1

 //   @virtualize
 //   def main() {
 //     type T = Int

 //     val frameRows = 64
 //     val frameCols = 64
 //     val onboardVideo = target.VideoCamera
 //     val mem = DRAM[T](frameRows, frameCols)
 //     val conduit = StreamIn[T](onboardVideo)
 //     // val avalon = StreamOut()

 //    Accel {
 //      Foreach(*) { _ =>
 //        Stream(1 by 1) { i =>
          
 //          println("Hello!")

 //        }
 //      }
 //    }


 //     // // Raw Spatial streaming pipes
 //     // Accel {
 //     //   Foreach(*) { i =>
 //     //     val frameRow = SRAM[T](frameCols)
 //     //     Stream(frameCols by 1) { j =>
 //     //       Pipe {
 //     //         successor.enq(conduit.deq())
 //     //       }
 //     //       Pipe {
 //     //         successor.enq(predecessor.deq())
 //     //       }
 //     //       Pipe {
 //     //         frameRow(j) := predecessor.deq()
 //     //       }
 //     //     }
 //     //     val row = i % frameRows
 //     //     mem(i, 0::frameCols) store frameRow
 //     //   }
 //     // }

 //     /*Accel {
 //       Foreach(*) { i =>
 //         Stream(1 by 1) { j =>
 //           val decoder = Decoder(conduit) // type = stream child. Pops from conduit and pushes to self. Plop in altera_up_avalon_video_decoder
 //           // Optional Pipe that intercepts the decoded stream interface
 //           val dma = DMA(decoder) // type = stream child. Pops from decoder and pushes to Avalon interface. Plop in altera_up_avalon_video_dma_controller
 //         }
 //       }
 //     }*/


 //     /*while(1) {
 //       AXI_Master_Slave(mem) // Plop in ARM code
 //     }*/
 //   }

 // }