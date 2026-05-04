package com.patslaurel.resibo.verification

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object VerificationToolModule {
    @Provides
    @IntoSet
    fun providePerplexityDiscoveryTool(tool: PerplexityDiscoveryTool): VerificationTool = tool

    @Provides
    @IntoSet
    fun provideOfficialSourceTool(tool: OfficialSourceTool): VerificationTool = tool

    @Provides
    @IntoSet
    fun provideLocalEvidenceTool(tool: LocalEvidenceTool): VerificationTool = tool
}
