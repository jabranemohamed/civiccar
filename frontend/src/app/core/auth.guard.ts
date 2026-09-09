import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Guard des routes /admin : attend la résolution de session avant de décider
 * (refresh direct d'une page protégée), redirige vers /login sinon. Le serveur
 * reste l'autorité : chaque appel API repasse par les contrôles Spring Security.
 */
export const adminGuard: CanActivateFn = async (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.resolved()) {
    await auth.resolve();
  }
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { continue: state.url } });
};
