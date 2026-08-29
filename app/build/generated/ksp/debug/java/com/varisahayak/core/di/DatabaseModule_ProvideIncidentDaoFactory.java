package com.varisahayak.core.di;

import com.varisahayak.data.local.VariSahayakDatabase;
import com.varisahayak.data.local.dao.IncidentDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideIncidentDaoFactory implements Factory<IncidentDao> {
  private final Provider<VariSahayakDatabase> dbProvider;

  private DatabaseModule_ProvideIncidentDaoFactory(Provider<VariSahayakDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public IncidentDao get() {
    return provideIncidentDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideIncidentDaoFactory create(
      Provider<VariSahayakDatabase> dbProvider) {
    return new DatabaseModule_ProvideIncidentDaoFactory(dbProvider);
  }

  public static IncidentDao provideIncidentDao(VariSahayakDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideIncidentDao(db));
  }
}
