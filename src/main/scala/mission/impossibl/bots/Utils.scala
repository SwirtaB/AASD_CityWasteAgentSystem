package mission.impossibl.bots

import scala.util.Random

object Utils {
  def sampleNormal(mean: Int, sigma: Int): Int = {
    math.round(Random.nextGaussian() * sigma + mean).toInt
  }
  def sampleNormal(mean: Float, sigma: Float): Float = {
    (Random.nextGaussian() * sigma + mean).toFloat
  }

  def dist(destination: (Int, Int), location: (Int, Int)): Int =
    math.abs(destination._1 - location._1) + math.abs(destination._2 - location._2)
}

