package org.example;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class GPSVisualizer {
    private static final String WEBSOCKET_URL = "ws://localhost:4001"; //  адрес
    private static final Map<String, Double[]> satellites = new HashMap<>();
    private static Double[] objectPosition = new Double[2]; // {x, y} начальная позиция объекта
    private static JFrame frame;
    private static ChartPanel chartPanel;

    public static void main(String[] args) {
        createAndShowGUI(); // окно один раз
        connectWebSocket();
    }

    private static void createAndShowGUI() {
        frame = new JFrame("GPS Visualization");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null); // Центр окна на экране
        frame.setVisible(true);

        chartPanel = new ChartPanel(createChart());
        frame.setContentPane(chartPanel);
    }

    private static JFreeChart createChart() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        return ChartFactory.createScatterPlot(
                "GPS Visualization",
                "X Coordinate",
                "Y Coordinate",
                dataset
        );
    }

    public static void connectWebSocket() {
        try {
            WebSocketClient webSocketClient = new WebSocketClient(new URI(WEBSOCKET_URL)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("Connected to WebSocket server");
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("Received message: " + message); // Отладочный вывод
                    processMessage(message);
                    updateChart(); // Обновляем график
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Disconnected from WebSocket server");
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String satelliteId = json.getString("id");
            double x = json.getDouble("x");
            double y = json.getDouble("y");

            // Проверка наличия ключа "distance"
            if (json.has("distance")) {
                double distance = json.getDouble("distance");
                System.out.println("Distance from satellite " + satelliteId + ": " + distance);

            } else {
                System.out.println("Distance not found for satellite " + satelliteId);
            }

            //  координаты спутника или добавляем новый
            satellites.put(satelliteId, new Double[]{x, y});
            System.out.println("Added/Updated satellite " + satelliteId + " with coordinates (" + x + ", " + y + ")");

            // Если количество спутников равно 4, очищаем их
            if (satellites.size() == 4) {
                System.out.println("Clearing satellites because count is 4.");
                satellites.clear(); // Очищаем данные о спутниках
            }

            // Вычисление позиции объекта
            objectPosition = trilateration(); //  триангуляции
            System.out.println("Object position calculated: " +
                    (objectPosition[0] != null ? objectPosition[0] : "null") + ", " +
                    (objectPosition[1] != null ? objectPosition[1] : "null"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private static Double[] trilateration() {
        // Проверка, достаточно ли спутников для вычисления
        if (satellites.size() < 3) {
            // Не хватает спутников для триангуляции
            return new Double[]{null, null}; // Возвращаем null значения, чтобы указать на отсутствие данных
        }

        // Извлекаем спутники в массив
        Double[][] satellitePositions = new Double[satellites.size()][2];
        int index = 0;

        for (Double[] position : satellites.values()) {
            satellitePositions[index++] = position;
        }

        // 3 спутника для триангуляции
        Double[] pos1 = satellitePositions[0]; // Координаты первого спутника
        Double[] pos2 = satellitePositions[1]; // Координаты второго спутника
        Double[] pos3 = satellitePositions[2]; // Координаты третьего спутника

        // вычисление позиции объекта
        double x = (pos1[0] + pos2[0] + pos3[0]) / 3; // Среднее значение X
        double y = (pos1[1] + pos2[1] + pos3[1]) / 3; // Среднее значение Y

        return new Double[]{x, y};
    }

    private static void updateChart() {
        SwingUtilities.invokeLater(() -> {
            XYSeries satelliteSeries = new XYSeries("Satellites");
            for (Map.Entry<String, Double[]> entry : satellites.entrySet()) {
                Double[] position = entry.getValue();
                if (position != null && position.length == 2) { // Проверка, что позиция спутника валидна
                    satelliteSeries.add(position[0], position[1]);
                }
            }

            // Проверка на наличие спутников
            System.out.println("Satellites count: " + satellites.size());

            // Создаем новую коллекцию данных для графика
            XYSeriesCollection dataset = new XYSeriesCollection();
            dataset.addSeries(satelliteSeries);

            // Добавляем объект
            XYSeries objectSeries = new XYSeries("Object");

            // Проверка, что objectPosition не null и содержит валидные значения
            if (objectPosition != null && objectPosition.length == 2 && objectPosition[0] != null && objectPosition[1] != null) {
                objectSeries.add(objectPosition[0], objectPosition[1]);
            } else {
                // Если objectPosition недоступна, добавляем фиксированную точку
                objectSeries.add(0.0, 0.0);
            }
            dataset.addSeries(objectSeries);

            JFreeChart chart = ChartFactory.createScatterPlot(
                    "GPS Visualization",
                    "X Coordinate",
                    "Y Coordinate",
                    dataset
            );

            chartPanel.setChart(chart); // Обновляем график без создания нового окна
        });
    }
}
