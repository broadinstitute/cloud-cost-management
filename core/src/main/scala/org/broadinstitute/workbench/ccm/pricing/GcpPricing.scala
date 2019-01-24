package org.broadinstitute.workbench.ccm
package pricing

import cats.effect.Sync
import cats.implicits._
import org.broadinstitute.workbench.ccm.pricing.JsonCodec._
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client


final case class Prices(diskCostPerGbPerHour: Double, CPUPrice: Double, RAMPrice: Double)

final case class PriceListKey(region: Region, machineType: MachineType, diskType: DiskType, usageType: UsageType, extended: Boolean)

final case class PriceList(prices: Map[PriceListKey, Prices])

class GcpPricing[F[_]: Sync](httpClient: Client[F], uri: Uri) {

  def getGcpPriceList(): F[GooglePriceList] = {
    httpClient.expect[GooglePriceList](uri)
  }


}


object GcpPricing {

  def getPriceList(googlePriceList: GooglePriceList): Either[Throwable, PriceList] = {

    def getPrice(region: Region, resourceFamily: ResourceFamily, resourceGroup: ResourceGroup, usageType: UsageType, descriptionShouldInclude: Option[String], descriptionShouldNotInclude: Option[String]): Either[String, Double] = {
      val sku = googlePriceList.priceItems.filter { priceItem =>
        (priceItem.regions.contains(region)
          && priceItem.category.resourceFamily.equals(resourceFamily)
          && priceItem.category.resourceGroup.equals(resourceGroup)
          && priceItem.category.usageType.equals(usageType)
          && (descriptionShouldInclude match {
          case Some(desc) => priceItem.description.asString.contains(desc)
          case None => true})
          && (descriptionShouldNotInclude match {
          case Some(desc) => !priceItem.description.asString.contains(desc)
          case None => true}))
      }
      sku.length match {
        case 0  => Left(s"No SKUs matched with region $region, resourceFamily $resourceFamily, resourceGroup $resourceGroup, $usageType usageType, and description including $descriptionShouldInclude and notIncluding $descriptionShouldNotInclude in the following price list: ${googlePriceList.priceItems}")
        case 1 => Right(getPriceFromSku(sku.head))
        case tooMany => Left(s"$tooMany SKUs matched with region $region, resourceFamily $resourceFamily, resourceGroup $resourceGroup, $usageType usageType, and description including $descriptionShouldInclude and notIncluding $descriptionShouldNotInclude in the following price list: ${googlePriceList.priceItems}")
      }
    }

    def getPriceFromSku(priceItem: GooglePriceItem): Double = {
      // ToDo: Currently just takes first, make it take either most recent or make it dependent on when the call ran
      priceItem.pricingInfo.head.tieredRates.filter(rate => rate.startUsageAmount.asInt == 0).head.nanos.asInt.toDouble / 1000000000
    }

    val thing = Region.allRegions.map { region =>
      MachineType.allMachineTypes.map { machineType =>
        DiskType.allDiskTypes.map { diskType =>
          UsageType.allUsageTypes.map { usageType =>
            for {
              diskCostPerGbMonth <- getPrice(region, ResourceFamily("Storage"), ResourceGroup(diskType.asString), UsageType.stringToUsageType(usageType.asString), None, Some("Regional"))
              cpuPrice <- getPrice(region, ResourceFamily("Compute"), ResourceGroup("CPU"), UsageType.stringToUsageType(usageType.asString), None, None)
              ramPrice <- getPrice(region, ResourceFamily("Compute"), ResourceGroup("RAM"), UsageType.stringToUsageType(usageType.asString), None, Some("Custom Extended"))
            } yield {
              val diskCostPerGbPerHour = diskCostPerGbMonth / (24 * 365 / 12)
              // currently assuming none of the ram is extended
              (PriceListKey(region, machineType, diskType, usageType, false), Prices(diskCostPerGbPerHour, cpuPrice, ramPrice))
            }
          }
        }
      }
    }

    val tuples: Seq[Either[String, (PriceListKey, Prices)]] = for {
      region <- Region.allRegions
      machineType <- MachineType.allMachineTypes
      diskType <- DiskType.allDiskTypes
      usageType <- UsageType.allUsageTypes
    } yield {
      for {
        diskCostPerGbMonth <- getPrice(region, ResourceFamily("Storage"), ResourceGroup(diskType.asString), UsageType.stringToUsageType(usageType.asString), None, Some("Regional"))
        cpuPrice <- getPrice(region, ResourceFamily("Compute"), ResourceGroup("CPU"), UsageType.stringToUsageType(usageType.asString), None, None)
        ramPrice <- getPrice(region, ResourceFamily("Compute"), ResourceGroup("RAM"), UsageType.stringToUsageType(usageType.asString), None, Some("Custom Extended"))
      } yield {
        (PriceListKey(region, machineType, diskType, usageType, false), Prices(diskCostPerGbMonth / (24 * 365 / 12), cpuPrice, ramPrice))
      }
    }

    val parseq: Either[Throwable, PriceList] = tuples.toList.parSequence.leftMap(errors => new Exception(errors.toList.mkString(", "))).map(x => PriceList(x.toMap))
    parseq

  }

}


//CUSTOM_MACHINE_CPU = "CP-DB-PG-CUSTOM-VM-CORE"
//CUSTOM_MACHINE_RAM = "CP-DB-PG-CUSTOM-VM-RAM"
//CUSTOM_MACHINE_EXTENDED_RAM = "CP-COMPUTEENGINE-CUSTOM-VM-EXTENDED-RAM"
//CUSTOM_MACHINE_CPU_PREEMPTIBLE = "CP-COMPUTEENGINE-CUSTOM-VM-CORE-PREEMPTIBLE"
//CUSTOM_MACHINE_RAM_PREEMPTIBLE = "CP-COMPUTEENGINE-CUSTOM-VM-RAM-PREEMPTIBLE"
//CUSTOM_MACHINE_EXTENDED_RAM_PREEMPTIBLE = "CP-COMPUTEENGINE-CUSTOM-VM-EXTENDED-RAM-PREEMPTIBLE"
//CUSTOM_MACHINE_TYPES = [CUSTOM_MACHINE_CPU,
//CUSTOM_MACHINE_RAM,
//CUSTOM_MACHINE_EXTENDED_RAM,
//CUSTOM_MACHINE_CPU_PREEMPTIBLE,
//CUSTOM_MACHINE_RAM_PREEMPTIBLE,
//CUSTOM_MACHINE_EXTENDED_RAM_PREEMPTIBLE]