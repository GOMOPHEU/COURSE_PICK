import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { CourseMapProps } from './course-map';
import { colors } from './ui';

const height = 216;
const inset = 30;

export function RoutePreview({ origin, course }: CourseMapProps) {
  const [width, setWidth] = useState(0);
  const stops = [{ ...origin, order: 0 }, ...course.places];
  const longitudes = stops.map(stop => stop.lng);
  const latitudes = stops.map(stop => stop.lat);
  const west = Math.min(...longitudes);
  const north = Math.max(...latitudes);
  const longitudeSpan = Math.max(...longitudes) - west;
  const latitudeSpan = north - Math.min(...latitudes);
  const scale = Math.min((width - 2 * inset) / Math.max(longitudeSpan * 0.8, 0.0001),
    (height - 2 * inset) / Math.max(latitudeSpan, 0.0001));
  const points = stops.map(stop => ({
    ...stop,
    x: width / 2 + (stop.lng - west - longitudeSpan / 2) * 0.8 * scale,
    y: height / 2 + (north - stop.lat - latitudeSpan / 2) * scale,
  }));

  return (
    <View accessible accessibilityLabel={'방문 순서: ' + stops.map(stop => stop.name).join(', ')}
      onLayout={event => setWidth(event.nativeEvent.layout.width)} style={styles.preview}>
      {width > 2 * inset && points.slice(1).map((to, index) => {
        const from = points[index];
        const dx = to.x - from.x;
        const dy = to.y - from.y;
        const length = Math.hypot(dx, dy);
        return <View key={to.order} style={[styles.line, {
          width: length, left: (from.x + to.x - length) / 2, top: (from.y + to.y) / 2 - 1,
          transform: [{ rotate: Math.atan2(dy, dx) + 'rad' }],
        }]} />;
      })}
      {width > 2 * inset && points.map(({ order, x, y }) => (
        <View key={order} style={[styles.pin, order === 0 && styles.origin, { left: x - 15, top: y - 15 }]}>
          <Text style={styles.label}>{order || '출발'}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  preview: { height, backgroundColor: '#eaf1eb', borderRadius: 13, overflow: 'hidden' },
  line: { position: 'absolute', height: 2, backgroundColor: '#759788' },
  pin: { position: 'absolute', width: 30, height: 30, borderRadius: 15, borderWidth: 2, borderColor: colors.surface,
    backgroundColor: '#3d6474', alignItems: 'center', justifyContent: 'center' },
  origin: { backgroundColor: colors.primary },
  label: { color: colors.surface, fontSize: 10, fontWeight: '700' },
});
