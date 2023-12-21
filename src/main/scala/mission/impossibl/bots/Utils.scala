package mission.impossibl.bots

import scala.util.Random

object Utils {
  def sample_normal(mean: Int, sigma: Int): Int = {
    math.round(Random.nextGaussian() * sigma + mean).toInt
  }
  def sample_normal(mean: Float, sigma: Float): Float = {
    (Random.nextGaussian() * sigma + mean).toFloat
  }
}

