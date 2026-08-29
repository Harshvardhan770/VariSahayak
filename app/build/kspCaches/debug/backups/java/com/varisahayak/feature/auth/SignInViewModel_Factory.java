package com.varisahayak.feature.auth;

import com.varisahayak.domain.repository.AuthRepository;
import com.varisahayak.domain.repository.ProfileRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class SignInViewModel_Factory implements Factory<SignInViewModel> {
  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<ProfileRepository> profileRepositoryProvider;

  private SignInViewModel_Factory(Provider<AuthRepository> authRepositoryProvider,
      Provider<ProfileRepository> profileRepositoryProvider) {
    this.authRepositoryProvider = authRepositoryProvider;
    this.profileRepositoryProvider = profileRepositoryProvider;
  }

  @Override
  public SignInViewModel get() {
    return newInstance(authRepositoryProvider.get(), profileRepositoryProvider.get());
  }

  public static SignInViewModel_Factory create(Provider<AuthRepository> authRepositoryProvider,
      Provider<ProfileRepository> profileRepositoryProvider) {
    return new SignInViewModel_Factory(authRepositoryProvider, profileRepositoryProvider);
  }

  public static SignInViewModel newInstance(AuthRepository authRepository,
      ProfileRepository profileRepository) {
    return new SignInViewModel(authRepository, profileRepository);
  }
}
