import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.gatk.phonehome.GATKRunReport


  // ToDos:
  // reduce the scope of the datasets so the script is more nimble
  // figure out how to give names to all the Queue-LSF logs (other than Q-1931@node1434-24.out) so that it is easier to find logs for certain steps
  // create gold standard BAQ'd bam files, no reason to always do it on the fly

  // Analysis to add at the end of the script:
  // auto generation of the cluster plots
  // spike in NA12878 to the exomes and to the lowpass, analysis of how much of her variants are being recovered compared to single sample exome or HiSeq calls
  // produce Kiran's Venn plots based on comparison between new VCF and gold standard produced VCF


class MethodsDevelopmentCallingPipeline extends QScript {
  qscript =>

  @Argument(shortName="gatk", doc="gatk jar file", required=true)
  var gatkJarFile: File = _

  @Argument(shortName="outputDir", doc="output directory", required=true)
  var outputDir: String = "./"

  @Argument(shortName="skipCalling", doc="skip the calling part of the pipeline and only run VQSR on preset, gold standard VCF files", required=false)
  var skipCalling: Boolean = false

  @Argument(shortName="dataset", doc="selects the datasets to run. If not provided, all datasets will be used", required=false)
  var datasets: List[String] = Nil

  @Argument(shortName="skipGoldStandard", doc="doesn't run the pipeline with the goldstandard VCF files for comparison", required=false)
  var skipGoldStandard: Boolean = false

  @Argument(shortName="noBAQ", doc="turns off BAQ calculation", required=false)
  var noBAQ: Boolean = false

  @Argument(shortName="noMASK", doc="turns off MASK calculation", required=false)
  var noMASK: Boolean = false

  @Argument(shortName="eval", doc="adds the VariantEval walker to the pipeline", required=false)
  var eval: Boolean = false

  @Argument(shortName="noCut", doc="removes the ApplyVariantCut walker from the pipeline", required=false)
  var noCut: Boolean = false

  @Argument(shortName="indels", doc="calls indels with the Unified Genotyper", required=false)
  var callIndels: Boolean = false

  @Argument(shortName="LOCAL_ET", doc="Doesn't use the AWS S3 storage for ET option", required=false)
  var LOCAL_ET: Boolean = false

  trait UNIVERSAL_GATK_ARGS extends CommandLineGATK {
    logging_level = "INFO";
    jarFile = gatkJarFile;
    memoryLimit = Some(3);
    phone_home = Some(if ( LOCAL_ET ) GATKRunReport.PhoneHomeOption.STANDARD else GATKRunReport.PhoneHomeOption.AWS_S3)
  }

  class Target(
          val baseName: String,
          val reference: File,
          val dbsnpFile: String,
          val hapmapFile: String,
          val maskFile: String,
          val bamList: File,
          val goldStandard_VCF: File,
          val intervals: String,
          val titvTarget: Double,
          val trancheTarget: Double,
          val isLowpass: Boolean) {
    val name = qscript.outputDir + baseName
    val clusterFile = new File(name + ".clusters")
    val rawVCF = new File(name + ".raw.vcf")
    val rawIndelVCF = new File(name + ".raw.indel.vcf")
    val filteredVCF = new File(name + ".filtered.vcf")
    val filteredIndelVCF = new File(name + ".filtered.indel.vcf")
    val titvRecalibratedVCF = new File(name + ".titv.recalibrated.vcf")
    val titvTranchesFile = new File(name + ".titv.tranches")
    val tsRecalibratedVCF = new File(name + ".ts.recalibrated.vcf")
    val tsTranchesFile = new File(name + ".ts.tranches")
    val cutVCF = new File(name + ".cut.vcf")
    val evalFile = new File(name + ".snp.eval")
    val evalIndelFile = new File(name + ".indel.eval")
    val goldStandardName = qscript.outputDir + "goldStandard/" + baseName
    val goldStandardClusterFile = new File(goldStandardName + ".clusters")
  }

  val hg19 = new File("/seq/references/Homo_sapiens_assembly19/v1/Homo_sapiens_assembly19.fasta")  
  val hg18 = new File("/seq/references/Homo_sapiens_assembly18/v0/Homo_sapiens_assembly18.fasta")
  val b36 = new File("/humgen/1kg/reference/human_b36_both.fasta")
  val b37 = new File("/humgen/1kg/reference/human_g1k_v37.fasta")
  val dbSNP_hg18_129 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/dbSNP/dbsnp_129_hg18.rod"            // Special case for NA12878 collections that can't use 132 because they are part of it.
  val dbSNP_b36 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/dbSNP/dbsnp_129_b36.rod"
  val dbSNP_b37 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/dbSNP/dbsnp_132_b37.leftAligned.vcf"
  val dbSNP_b37_129 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/dbSNP/dbsnp_129_b37.leftAligned.vcf"              // Special case for NA12878 collections that can't use 132 because they are part of it.
  val hapmap_hg18 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/HapMap/3.3/sites_r27_nr.hg18_fwd.vcf"
  val hapmap_b36 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/HapMap/3.3/sites_r27_nr.b36_fwd.vcf"
  val hapmap_b37 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/HapMap/3.3/sites_r27_nr.b37_fwd.vcf"
  val training_hapmap_b37 = "/humgen/1kg/processing/pipeline_test_bams/hapmap3.3_training_chr20.vcf"
  val omni_b36 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/Omni2.5_chip/1212samples.b36.vcf"
  val omni_b37 = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Validated/Omni2.5_chip/1212samples.b37.vcf"
  val indelMask_b36 = "/humgen/1kg/processing/pipeline_test_bams/pilot1.dindel.mask.b36.bed"
  val indelMask_b37 = "/humgen/1kg/processing/pipeline_test_bams/pilot1.dindel.mask.b37.bed"

  val lowPass: Boolean = true
  val indels: Boolean = true
  val useMask: Boolean = !noMASK
  val useCut: Boolean = !noCut

  val queueLogDir = ".qlog/"
  
  val targetDataSets: Map[String, Target] = Map(
    "HiSeq" -> new Target("NA12878.HiSeq", hg18, dbSNP_hg18_129, hapmap_hg18,
              "/humgen/gsa-hpprojects/dev/depristo/oneOffProjects/1000GenomesProcessingPaper/wgs.v13/HiSeq.WGS.cleaned.indels.10.mask",
              new File("/humgen/gsa-hpprojects/NA12878Collection/bams/NA12878.HiSeq.WGS.bwa.cleaned.recal.bam"),
              new File("/home/radon01/depristo/work/oneOffProjects/1000GenomesProcessingPaper/wgs.v13/HiSeq.WGS.cleaned.ug.snpfiltered.indelfiltered.vcf"),
              "/humgen/1kg/processing/pipeline_test_bams/whole_genome_chunked.hg18.intervals", 2.07, 1.0, !lowPass),
    "HiSeq19" -> new Target("NA12878.HiSeq19", hg19, dbSNP_b37_129, hapmap_b37, indelMask_b37,
              new File("/humgen/gsa-hpprojects/NA12878Collection/bams/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.bam"),
              new File("/humgen/gsa-hpprojects/dev/carneiro/hiseq19/analysis/snps/NA12878.HiSeq19.filtered.vcf"),
              "/humgen/1kg/processing/pipeline_test_bams/whole_genome_chunked.hg19.intervals", 2.3, 0.5, !lowPass),
    "GA2hg19" -> new Target("NA12878.GA2.hg19", hg19, dbSNP_b37_129, hapmap_b37, indelMask_b37,
              new File("/humgen/gsa-hpprojects/NA12878Collection/bams/NA12878.GA2.WGS.bwa.cleaned.hg19.bam"),
              new File("/humgen/gsa-hpprojects/dev/carneiro/hiseq19/analysis/snps/NA12878.GA2.hg19.filtered.vcf"),
              "/humgen/1kg/processing/pipeline_test_bams/whole_genome_chunked.hg19.intervals", 2.3, 1.0, !lowPass),
    "WEx" -> new Target("NA12878.WEx", hg18, dbSNP_hg18_129, hapmap_hg18,
              "/humgen/gsa-hpprojects/dev/depristo/oneOffProjects/1000GenomesProcessingPaper/wgs.v13/GA2.WEx.cleaned.indels.10.mask",
              new File("/humgen/gsa-hpprojects/NA12878Collection/bams/NA12878.WEx.cleaned.recal.bam"),
              new File("/home/radon01/depristo/work/oneOffProjects/1000GenomesProcessingPaper/wgs.v13/GA2.WEx.cleaned.ug.snpfiltered.indelfiltered.vcf"),
              "/seq/references/HybSelOligos/whole_exome_agilent_1.1_refseq_plus_3_boosters/whole_exome_agilent_1.1_refseq_plus_3_boosters.targets.interval_list", 2.6, 3.0, !lowPass),
    "WExTrio" -> new Target("CEUTrio.WEx", hg19, dbSNP_b37_129, hapmap_b37, indelMask_b37,
              new File("/humgen/gsa-hpprojects/NA12878Collection/bams/CEUTrio.HiSeq.WEx.bwa.cleaned.recal.bam"),
              new File("/humgen/gsa-hpprojects/dev/carneiro/trio/analysis/snps/CEUTrio.WEx.filtered.vcf"),
              "/seq/references/HybSelOligos/whole_exome_agilent_1.1_refseq_plus_3_boosters/whole_exome_agilent_1.1_refseq_plus_3_boosters.Homo_sapiens_assembly19.targets.interval_list", 2.6, 3.0, !lowPass),
    "FIN" -> new Target("FIN", b37, dbSNP_b37, hapmap_b37, indelMask_b37,
              new File("/humgen/1kg/processing/pipeline_test_bams/FIN.79sample.Nov2010.chr20.bam"),
              new File("/humgen/gsa-hpprojects/dev/data/AugChr20Calls_v4_3state/ALL.august.v4.chr20.filtered.vcf"),         // ** THIS GOLD STANDARD NEEDS TO BE CORRECTED **
              "/humgen/1kg/processing/pipeline_test_bams/whole_genome_chunked.chr20.hg19.intervals", 2.3, 1.0, lowPass),
    "TGPWExGdA" -> new Target("1000G.WEx.GdA", b37, dbSNP_b37, hapmap_b37, indelMask_b37,
              new File("/humgen/1kg/processing/pipeline_test_bams/Barcoded_1000G_WEx_Reduced_Plate_1.cleaned.list"),        // BUGBUG: reduce from 60 to 20 people
              new File("/humgen/gsa-scr1/delangel/NewUG/calls/AugustRelease.filtered_Q50_QD5.0_SB0.0.allSamples.SNPs_hg19.WEx_UG_newUG_MQC.vcf"), // ** THIS GOLD STANDARD NEEDS TO BE CORRECTED **
              "/seq/references/HybSelOligos/whole_exome_agilent_1.1_refseq_plus_3_boosters/whole_exome_agilent_1.1_refseq_plus_3_boosters.Homo_sapiens_assembly19.targets.interval_list", 2.6, 1.0, !lowPass),
    "LowPassN60" -> new Target("lowpass.N60", b36, dbSNP_b36, hapmap_b36, indelMask_b36,
              new File("/humgen/1kg/analysis/bamsForDataProcessingPapers/lowpass_b36/lowpass.chr20.cleaned.matefixed.bam"), // the bam list to call from
              new File("/home/radon01/depristo/work/oneOffProjects/VQSRCutByNRS/lowpass.N60.chr20.filtered.vcf"),           // the gold standard VCF file to run through the VQSR
              "/humgen/1kg/processing/pipeline_test_bams/whole_genome_chunked.chr20.b36.intervals", 2.3, 1.0, lowPass),          // chunked interval list to use with Queue's scatter/gather functionality
    "LowPassEUR363Nov" -> new Target("EUR.nov2010", b37, dbSNP_b37, hapmap_b37, indelMask_b37,
              new File("/humgen/1kg/processing/pipeline_test_bams/EUR.363sample.Nov2010.chr20.bam"),
              new File("/humgen/gsa-hpprojects/dev/data/AugChr20Calls_v4_3state/ALL.august.v4.chr20.filtered.vcf"),         // ** THIS GOLD STANDARD NEEDS TO BE CORRECTED **
              "/humgen/1kg/processing/pipeline_test_bams/whole_genome_chunked.chr20.hg19.intervals", 2.3, 1.0, lowPass)
  )


  def script = {

    // Selects the datasets in the -dataset argument and adds them to targets.
    var targets: List[Target] = List()
    if (!datasets.isEmpty)
      for (ds <- datasets)
        targets ::= targetDataSets(ds)                  // Could check if ds was mispelled, but this way an exception will be thrown, maybe it's better this way?
    else                                                // If -dataset is not specified, all datasets are used.
      for (targetDS <- targetDataSets.valuesIterator)   // for Scala 2.7 or older, use targetDataSets.values
        targets ::= targetDS

    val goldStandard = true
    for (target <- targets) {
      if( !skipCalling ) {
        if (callIndels) add(new indelCall(target), new indelFilter(target), new indelEvaluation(target))
        add(new snpCall(target))
        add(new snpFilter(target))
        add(new GenerateClusters(target, !goldStandard))
        add(new VariantRecalibratorNRS(target, !goldStandard))
        if (!noCut) add(new VariantCut(target))
        if (eval) add(new snpEvaluation(target))
      }
      if ( !skipGoldStandard ) {
        add(new GenerateClusters(target, goldStandard))
        add(new VariantRecalibratorNRS(target, goldStandard))
      }
    }
  }

  def bai(bam: File) = new File(bam + ".bai")

  val FiltersToIgnore = List("DPFilter", "ABFilter", "ESPStandard", "QualByDepth", "StrandBias", "HomopolymerRun")

  // 1.) Unified Genotyper Base
  class GenotyperBase (t: Target) extends UnifiedGenotyper with UNIVERSAL_GATK_ARGS {
    this.reference_sequence = t.reference
    this.intervalsString ++= List(t.intervals)
    this.scatterCount = 63 // the smallest interval list has 63 intervals, one for each Mb on chr20
    this.dcov = Some( if ( t.isLowpass ) { 50 } else { 250 } )
    this.stand_call_conf = Some( if ( t.isLowpass ) { 4.0 } else { 30.0 } )
    this.stand_emit_conf = Some( if ( t.isLowpass ) { 4.0 } else { 30.0 } )
    this.input_file :+= t.bamList
    if (t.dbsnpFile.endsWith(".rod"))
      this.DBSNP = new File(t.dbsnpFile)
    else if (t.dbsnpFile.endsWith(".vcf"))
      this.rodBind :+= RodBind("dbsnp", "VCF", t.dbsnpFile)
  }

  // 1a.) Call SNPs with UG
  class snpCall (t: Target) extends GenotyperBase(t) {
    this.out = t.rawVCF
    this.baq = Some( if (noBAQ) {org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF} else {org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY})
    this.analysisName = t.name + "_UGs"
    this.jobName =  queueLogDir + t.name + ".snpcall"
  }

  // 1b.) Call Indels with UG
  class indelCall (t: Target) extends GenotyperBase(t) {
    this.out = t.rawIndelVCF
    this.glm = Some(org.broadinstitute.sting.gatk.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.DINDEL)
    this.baq = Some(org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.OFF)
    this.analysisName = t.name + "_UGi"
    this.jobName =  queueLogDir + t.name + ".indelcall"
  }

  // 2.) Hard Filtering Base
  class FilterBase (t: Target) extends VariantFiltration with UNIVERSAL_GATK_ARGS {
    this.reference_sequence = t.reference
    this.intervalsString ++= List(t.intervals)
    this.scatterCount = 10
    this.filterName ++= List("HARD_TO_VALIDATE")
    this.filterExpression ++= List("\"MQ0 >= 4 && (MQ0 / (1.0 * DP)) > 0.1\"")
  }

  // 2a.) Hard Filter for SNPs (soon to be obsolete)
  class snpFilter (t: Target) extends FilterBase(t) {
    this.variantVCF = t.rawVCF
    this.out = t.filteredVCF
    if (useMask) {
      this.rodBind :+= RodBind("mask", "Bed", t.maskFile)
      this.maskName = "InDel"
    }
    this.analysisName = t.name + "_VF"
    this.jobName =  queueLogDir + t.name + ".snpfilter"
  }

   // 2b.) Hard Filter for Indels
  class indelFilter (t: Target) extends FilterBase(t) {
    this.variantVCF = t.rawIndelVCF
    this.out = t.filteredIndelVCF
    this.filterName ++= List("LowQual", "StrandBias", "QualByDepth", "HomopolymerRun")
    if (t.isLowpass)
      this.filterExpression ++= List("\"QUAL<30.0\"", "\"SB>=-1.0\"", "\"QD<1.0\"", "\"HRun>=15\"")
    else
      this.filterExpression ++= List("\"QUAL<50.0\"", "\"SB>=-1.0\"", "\"QD<5.0\"", "\"HRun>=15\"")
    this.analysisName = t.name + "_VF"
    this.jobName =  queueLogDir + t.name + ".indelfilter"
  }

  // 3.) VQSR part1 Generate Gaussian clusters based on truth sites
  class GenerateClusters(t: Target, goldStandard: Boolean) extends GenerateVariantClusters with UNIVERSAL_GATK_ARGS {
    val name: String = if ( goldStandard ) { t.goldStandardName } else { t.name }
    this.reference_sequence = t.reference
    this.rodBind :+= RodBind("hapmap", "VCF", t.hapmapFile)
    if( t.hapmapFile.contains("b37") )
      this.rodBind :+= RodBind("1kg", "VCF", omni_b37)
    else if( t.hapmapFile.contains("b36") )
      this.rodBind :+= RodBind("1kg", "VCF", omni_b36)
    this.rodBind :+= RodBind("input", "VCF", if ( goldStandard ) { t.goldStandard_VCF } else { t.filteredVCF } )
    this.clusterFile = if ( goldStandard ) { t.goldStandardClusterFile } else { t.clusterFile }
    this.use_annotation ++= List("QD", "SB", "HaplotypeScore", "HRun")
    this.intervalsString ++= List(t.intervals)
    this.qual = Some(100) // clustering parameters to be updated soon pending new experimentation results
    this.std = Some(3.5)
    this.mG = Some(8)
    this.ignoreFilter ++= FiltersToIgnore
    this.analysisName = name + "_GVC"
    this.jobName =  queueLogDir + t.name + ".cluster"
    if (t.dbsnpFile.endsWith(".rod"))
      this.DBSNP = new File(t.dbsnpFile)
    else if (t.dbsnpFile.endsWith(".vcf"))
      this.rodBind :+= RodBind("dbsnp", "VCF", t.dbsnpFile)
    this.trustAllPolymorphic = true
  }

  // 4.) VQSR part2 Calculate new LOD for all input SNPs by evaluating the Gaussian clusters
  class VariantRecalibratorNRS(t: Target, goldStandard: Boolean) extends VariantRecalibrator with UNIVERSAL_GATK_ARGS {
    val name: String = if ( goldStandard ) { t.goldStandardName } else { t.name }
    this.reference_sequence = t.reference
    if( t.hapmapFile.contains("b37") ) {
      this.rodBind :+= RodBind("1kg", "VCF", omni_b37)
      this.rodBind :+= RodBind("truthOmni", "VCF", omni_b37)
    } else if( t.hapmapFile.contains("b36") ) {
      this.rodBind :+= RodBind("1kg", "VCF", omni_b36)
      this.rodBind :+= RodBind("truthOmni", "VCF", omni_b36)
    }
    this.rodBind :+= RodBind("hapmap", "VCF", t.hapmapFile)
    this.rodBind :+= RodBind("truthHapMap", "VCF", t.hapmapFile)
    this.rodBind :+= RodBind("input", "VCF", if ( goldStandard ) { t.goldStandard_VCF } else { t.filteredVCF } )
    this.clusterFile = if ( goldStandard ) { t.goldStandardClusterFile } else { t.clusterFile }
    this.analysisName = name + "_VR"
    this.intervalsString ++= List(t.intervals)
    this.ignoreFilter ++= FiltersToIgnore
    this.ignoreFilter ++= List("HARD_TO_VALIDATE")
    if (t.dbsnpFile.endsWith(".rod"))
      this.DBSNP = new File(t.dbsnpFile)
    else if (t.dbsnpFile.endsWith(".vcf"))
      this.rodBind :+= RodBind("dbsnp", "VCF", t.dbsnpFile)
    this.sm = Some(org.broadinstitute.sting.gatk.walkers.variantrecalibration.VariantRecalibrator.SelectionMetricType.TRUTH_SENSITIVITY)
    this.tranche ++= List("0.1", "0.5", "0.7", "1.0", "3.0", "5.0", "10.0", "100.0")
    this.out = t.tsRecalibratedVCF
    this.tranchesFile = t.tsTranchesFile
    this.jobName =  queueLogDir + t.name + ".nrs"
    this.trustAllPolymorphic = true
  }

   // 5.) Variant Cut filter out the variants marked by recalibration to the 99% tranche
  class VariantCut(t: Target) extends ApplyVariantCuts with UNIVERSAL_GATK_ARGS {
    this.reference_sequence = t.reference
    this.rodBind :+= RodBind("input", "VCF",  t.tsRecalibratedVCF )
    this.intervalsString ++= List(t.intervals)
    this.out = t.cutVCF
    this.tranchesFile = t.tsTranchesFile
    this.fdr_filter_level = Some(t.trancheTarget)
    this.analysisName = t.name + "_VC"
    this.jobName =  queueLogDir + t.name + ".cut"
    if (t.dbsnpFile.endsWith(".rod"))
      this.DBSNP = new File(t.dbsnpFile)
    else if (t.dbsnpFile.endsWith(".vcf"))
      this.rodBind :+= RodBind("dbsnp", "VCF", t.dbsnpFile)
  }

  // 6.) Variant Evaluation Base(OPTIONAL)
  class EvalBase(t: Target) extends VariantEval with UNIVERSAL_GATK_ARGS {
    this.reference_sequence = t.reference
    this.rodBind :+= RodBind("comphapmap", "VCF", t.hapmapFile)
    this.intervalsString ++= List(t.intervals)
    if (t.dbsnpFile.endsWith(".rod"))
      this.DBSNP = new File(t.dbsnpFile)
    else if (t.dbsnpFile.endsWith(".vcf"))
      this.rodBind :+= RodBind("dbsnp", "VCF", t.dbsnpFile)
  }

  // 6a.) SNP Evaluation (OPTIONAL) based on the cut vcf
  class snpEvaluation(t: Target) extends EvalBase(t) {
    if (t.reference == b37 || t.reference == hg19) this.rodBind :+= RodBind("compomni", "VCF", omni_b37)
    this.rodBind :+= RodBind("eval", "VCF", if (useCut) {t.cutVCF} else {t.tsRecalibratedVCF} )
    this.out =  t.evalFile
    this.analysisName = t.name + "_VEs"
    this.jobName =  queueLogDir + t.name + ".snp.eval"
  }

  // 6b.) Indel Evaluation (OPTIONAL)
  class indelEvaluation(t: Target) extends EvalBase(t) {
    this.rodBind :+= RodBind("eval", "VCF", t.filteredIndelVCF)
    this.evalModule :+= "IndelStatistics"
    this.out =  t.evalIndelFile
    this.analysisName = t.name + "_VEi"
    this.jobName =  queueLogDir + queueLogDir + t.name + ".indel.eval"
  }
}
