import type { Course } from './contracts';
import { RoutePreview } from './route-preview';

export interface CourseMapProps {
  origin: { name: string; lat: number; lng: number };
  course: Course;
}

export default RoutePreview;
