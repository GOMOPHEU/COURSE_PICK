import { useEffect, useState } from 'react';
import { ActivityIndicator, Image, StyleSheet, Text } from 'react-native';

import { getPlaceDetails } from './api';
import { themes, type Place } from './contracts';
import { visitDateLabel } from './schedule';
import { SeoulIllustration } from './seoul-illustration';
import { Card, colors, Copy, ErrorText, Heading } from './ui';

const operatingStatus = { confirmed: '영업 확인', official: '공식 안내 기준', unknown: '운영정보 미확인' };

export function PlaceDetails({ place, date, demo }: { place: Place; date: string; demo: boolean }) {
  const [details, setDetails] = useState<Awaited<ReturnType<typeof getPlaceDetails>> | null>(null);
  const [photoFailed, setPhotoFailed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort('timeout'), 15000);
    let active = true;

    getPlaceDetails(place.kakaoId, date, controller.signal)
      .then(value => { if (active) setDetails(value); })
      .catch(cause => {
        if (!active) return;
        setError(controller.signal.aborted ? '장소 상세 응답이 지연됐어요.'
          : cause instanceof Error ? cause.message : '장소 상세 정보를 불러오지 못했어요.');
      })
      .finally(() => clearTimeout(timeout));

    return () => {
      active = false;
      clearTimeout(timeout);
      controller.abort();
    };
  }, [place.kakaoId, date, demo]);
  return (
    <>
      {details?.photoUrl && !photoFailed ? <Image source={{ uri: details.photoUrl }} accessibilityLabel="장소 사진"
        style={styles.photo} onError={() => setPhotoFailed(true)} /> : <SeoulIllustration />}
      <Text style={styles.title} accessibilityRole="header">{place.name}</Text>
      <Copy>{themes[place.theme]}</Copy>
      {demo && <Copy>시연용 장소 · 실제 사진과 주소는 미확인</Copy>}
      {error && <ErrorText message={error} />}
      {!demo && !details && !error && <ActivityIndicator color={colors.primary} accessibilityLabel="장소 상세 불러오는 중" />}
      {details && <Copy>{details.address ? '주소 (카카오) · ' + details.address : '주소 미확인'}</Copy>}
      <Card style={styles.hours}>
        <Heading>{visitDateLabel(date)} 운영정보</Heading>
        <Copy>{operatingStatus[place.openStatus]}</Copy>
        {details?.openingHours && <Copy>영업시간 {details.openingHours} · Google Maps</Copy>}
      </Card>
      {details?.rating !== undefined && <Copy>별점 {details.rating} · Google Maps</Copy>}
    </>
  );
}

const styles = StyleSheet.create({
  photo: { width: '100%', aspectRatio: 1.9, borderRadius: 12 },
  title: { color: colors.text, fontSize: 25, fontWeight: '800' },
  hours: { backgroundColor: colors.sand },
});
