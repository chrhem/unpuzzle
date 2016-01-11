/*
 * unpuzzle
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.unpuzzle

import org.akhikhl.unpuzzle.eclipse2maven.DependenciesConfig
import org.akhikhl.unpuzzle.eclipse2maven.EclipseDeployer
import org.akhikhl.unpuzzle.eclipse2maven.EclipseSource
import org.gradle.api.GradleException

/**
 * Plugin extension for {@link org.akhikhl.unpuzzle.UnpuzzlePlugin}
 * @author akhikhl
 */
class Config {

  Config parentConfig
  String configName
  File localMavenRepositoryDir
  File unpuzzleDir
  String selectedEclipseVersion
  Set<String> languagePacks = new LinkedHashSet()
  Map<String, List<Closure>> lazyVersions = [:]
  private Map<String, EclipseVersionConfig> versionConfigs = null
  Map uploadEclipse = [:]
  DependenciesConfig dependenciesConfig = new DependenciesConfig()
  def artifacts(Closure cl) {
    dependenciesConfig.call(cl)
  }

  // use this only for testing/debugging!
  boolean dryRun = false

  Config() {
  }

  Config(String configName) {
    this.configName = configName
  }

  void eclipseVersion(String versionString, Closure closure) {
    List<Closure> closureList = lazyVersions[versionString]
    if(closureList == null)
      closureList = lazyVersions[versionString] = []
    closureList.add(closure)
    versionConfigs = null
  }

  void languagePack(String language) {
    languagePacks.add(language)
  }

  File getEclipseUnpackDir() {
    def vconfig = getSelectedVersionConfig()
    def source = vconfig.sources.find { !it.url.contains('delta') && !it.url.contains('SDK') }
    if(source == null)
      throw new GradleException("Could not determine source for eclipse version ${selectedEclipseVersion}")
    EclipseDeployer.getUnpackDir(unpuzzleDir, source)
  }

  EclipseVersionConfig getSelectedVersionConfig() {
    getVersionConfigs()[selectedEclipseVersion]
  }

  Map<String, EclipseVersionConfig> getVersionConfigs() {
    if(versionConfigs == null) {
      Map m = [:]
      lazyVersions.each { String versionString, List<Closure> closureList ->
        def versionConfig = m[versionString] = new EclipseVersionConfig()
        for(Closure closure in closureList) {
          closure = closure.rehydrate(versionConfig, closure.owner, closure.thisObject)
          closure.resolveStrategy = Closure.DELEGATE_FIRST
          closure()
        }
        for(String language in languagePacks)
          versionConfig.languagePack(language)
      }
      versionConfigs = m
    }
    return versionConfigs.asImmutable()
  }

  protected static void merge(Config target, Config source) {
    if(source.parentConfig)
      merge(target, source.parentConfig)
    if(source.localMavenRepositoryDir != null)
      target.localMavenRepositoryDir = source.localMavenRepositoryDir
    if(source.unpuzzleDir != null)
      target.unpuzzleDir = source.unpuzzleDir
    if(source.selectedEclipseVersion != null)
      target.selectedEclipseVersion = source.selectedEclipseVersion
    target.languagePacks.addAll(source.languagePacks)
    source.lazyVersions.each { String versionString, List<Closure> sourceClosureList ->
      List<Closure> targetClosureList = target.lazyVersions[versionString]
      if(targetClosureList == null)
        targetClosureList = target.lazyVersions[versionString] = []
      targetClosureList.addAll(sourceClosureList)
    }
    target.uploadEclipse << source.uploadEclipse
    target.dependenciesConfig = source.dependenciesConfig
    if(source.dryRun)
      target.dryRun = true
  }
}
