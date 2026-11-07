package com.weatherhood.weather.domain;

public record Grid(int x, int y) {
    public static Grid from(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new WeatherException(WeatherException.Code.INVALID_REQUEST, "올바른 위도와 경도를 입력해주세요.");
        }
        // KMA DFS Lambert conformal conic projection, grid size 5 km.
        double radians = Math.PI / 180.0;
        double slat1 = 30 * radians;
        double slat2 = 60 * radians;
        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
                / Math.log(Math.tan(Math.PI / 4 + slat2 / 2) / Math.tan(Math.PI / 4 + slat1 / 2));
        double sf = Math.pow(Math.tan(Math.PI / 4 + slat1 / 2), sn) * Math.cos(slat1) / sn;
        double ro = (6371.00877 / 5) * sf / Math.pow(Math.tan(Math.PI / 4 + 38 * radians / 2), sn);
        double ra = (6371.00877 / 5) * sf / Math.pow(Math.tan(Math.PI / 4 + latitude * radians / 2), sn);
        double theta = (longitude - 126) * radians;
        if (theta > Math.PI) theta -= 2 * Math.PI;
        if (theta < -Math.PI) theta += 2 * Math.PI;
        theta *= sn;
        return new Grid((int) Math.floor(ra * Math.sin(theta) + 43.5),
                (int) Math.floor(ro - ra * Math.cos(theta) + 136.5));
    }
}
