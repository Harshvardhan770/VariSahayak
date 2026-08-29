package com.varisahayak.core.di

import com.varisahayak.data.repository.AuthRepositoryImpl
import com.varisahayak.data.repository.ClassificationRepositoryImpl
import com.varisahayak.data.repository.DeviceTokenRepositoryImpl
import com.varisahayak.data.repository.IncidentRepositoryImpl
import com.varisahayak.data.repository.LostFoundRepositoryImpl
import com.varisahayak.data.repository.ProfileRepositoryImpl
import com.varisahayak.data.repository.QrLocationRepositoryImpl
import com.varisahayak.data.repository.ResponderRepositoryImpl
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.ClassificationRepository
import com.varisahayak.domain.repository.DeviceTokenRepository
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.LostFoundRepository
import com.varisahayak.domain.repository.ProfileRepository
import com.varisahayak.domain.repository.QrLocationRepository
import com.varisahayak.domain.repository.ResponderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindQrLocationRepository(
        impl: QrLocationRepositoryImpl
    ): QrLocationRepository

    @Binds
    @Singleton
    abstract fun bindLostFoundRepository(
        impl: LostFoundRepositoryImpl
    ): LostFoundRepository

    @Binds
    @Singleton
    abstract fun bindIncidentRepository(
        impl: IncidentRepositoryImpl
    ): IncidentRepository

    @Binds
    @Singleton
    abstract fun bindResponderRepository(
        impl: ResponderRepositoryImpl
    ): ResponderRepository

    @Binds
    @Singleton
    abstract fun bindDeviceTokenRepository(
        impl: DeviceTokenRepositoryImpl
    ): DeviceTokenRepository

    @Binds
    @Singleton
    abstract fun bindClassificationRepository(
        impl: ClassificationRepositoryImpl
    ): ClassificationRepository
}
