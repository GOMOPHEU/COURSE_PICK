import Constants from 'expo-constants';
import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker, Polyline, PROVIDER_GOOGLE } from 'react-native-maps';

import type { CourseMapProps } from './course-map';
import { RoutePreview } from './route-preview';
import { Button, Card, colors, Copy } from './ui';

export default function CourseMap({ origin, course }: CourseMapProps) {
  const [attempt, setAttempt] = useState(0);
  if (!Constants.expoGoConfig && Constants.expoConfig?.extra?.hasAndroidMapsKey !== true) {
    return <RoutePreview origin={origin} course={course} />;
  }

  return <GoogleCourseMap key={attempt} origin={origin} course={course} onRetry={() => setAttempt(value => value + 1)} />;
}

function GoogleCourseMap({ origin, course, onRetry }: CourseMapProps & { onRetry: () => void }) {
  const [loaded, setLoaded] = useState(false);
  const [timedOut, setTimedOut] = useState(false);
  const points = [origin, ...course.places];
  const coordinates = points.map(point => ({ latitude: point.lat, longitude: point.lng }));
  const latitudes = points.map(point => point.lat);
  const longitudes = points.map(point => point.lng);
  const south = Math.min(...latitudes);
  const north = Math.max(...latitudes);
  const west = Math.min(...longitudes);
  const east = Math.max(...longitudes);

  useEffect(() => {
    if (loaded) return;
    const timer = setTimeout(() => setTimedOut(true), 12000);
    return () => clearTimeout(timer);
  }, [loaded]);

  return (
    <View style={styles.container}>
      {!timedOut && <MapView provider={PROVIDER_GOOGLE} style={styles.map} onMapLoaded={() => setLoaded(true)} initialRegion={{
        latitude: (south + north) / 2,
        longitude: (west + east) / 2,
        latitudeDelta: Math.max(0.006, (north - south) * 1.8),
        longitudeDelta: Math.max(0.006, (east - west) * 1.8),
      }}>
        <Polyline coordinates={coordinates} strokeColor={colors.primary} strokeWidth={3} />
        {points.map((point, index) => (
          <Marker key={index} coordinate={coordinates[index]} title={(index ? index + '. ' : '출발 · ') + point.name}>
            <View collapsable={false} style={styles.pin}><Text style={styles.pinText}>{index || '출발'}</Text></View>
          </Marker>
        ))}
      </MapView>}
      {!loaded && (timedOut ? (
        <>
          <RoutePreview origin={origin} course={course} />
          <Card>
            <Copy>지도 로딩이 지연되고 있어요. 연결 상태를 확인한 뒤 다시 시도해 주세요.</Copy>
            <Button title="지도 다시 불러오기" secondary onPress={onRetry} />
          </Card>
        </>
      ) : <ActivityIndicator color={colors.primary} accessibilityLabel="지도 불러오는 중" />)}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 10, borderRadius: 13, overflow: 'hidden' },
  map: { height: 300, width: '100%' },
  pin: { backgroundColor: colors.primary, borderColor: '#fff', borderWidth: 2, borderRadius: 16,
    minWidth: 32, height: 32, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 4 },
  pinText: { color: '#fff', fontWeight: '700' },
});
