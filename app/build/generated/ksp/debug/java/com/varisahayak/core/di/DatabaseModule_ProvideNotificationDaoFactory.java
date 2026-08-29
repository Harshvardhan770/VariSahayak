package com.varisahayak.core.di;

import com.varisahayak.data.local.VariSahayakDatabase;
import com.varisahayak.data.local.dao.NotificationDao;
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
public final class DatabaseModule_ProvideNotificationDaoFactory implements Factory<NotificationDao> {
  private final Provider<VariSahayakDatabase> dbProvider;

  private DatabaseModule_ProvideNotificationDaoFactory(Provider<VariSahayakDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public NotificationDao get() {
    return provideNotificationDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideNotificationDaoFactory create(
      Provider<VariSahayakDatabase> dbProvider) {
    return new DatabaseModule_ProvideNotificationDaoFactory(dbProvider);
  }

  public static NotificationDao provideNotificationDao(VariSahayakDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideNotificationDao(db));
  }
}
