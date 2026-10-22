
import { registerPino } from './lib/config/logging';

export async function register() {
  await registerPino();
}
