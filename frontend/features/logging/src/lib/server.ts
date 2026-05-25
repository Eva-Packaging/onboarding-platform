import pino from 'pino';
import { pinoOptions } from '../config';

const logger = pino(pinoOptions);

export default logger;