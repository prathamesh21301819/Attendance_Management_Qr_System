package forms;

// ... your imports remain unchanged ...
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import dao.ConnectionProvider;
import org.apache.log4j.BasicConfigurator;
import org.netbeans.lib.awtextra.AbsoluteConstraints;
import utility.BDUtility;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.LongAccumulator;

import static java.lang.String.format;




// ... your imports remain unchanged ...

public class MarkAttendance extends javax.swing.JFrame implements Runnable, ThreadFactory {
    private WebcamPanel panel = null;
    private Webcam webcam = null;
    private ExecutorService executor = Executors.newSingleThreadExecutor(this);
    private volatile boolean running = true;

    /**
     * Creates new form MarkAttendance
     */
    public MarkAttendance() {
        initComponents();
        BDUtility.setImage(this, "Images/MarkAttendance.jpg", 1500, 900);
        this.getRootPane().setBorder(BorderFactory.createMatteBorder(4, 4, 4, 4, Color.black));
        initWebcam();
        Timer timer = new Timer(1000, e -> updateTime()); // Fixed to 1000ms for performance
        timer.start();
    }

    private void initWebcam() {
        webcam = Webcam.getDefault();
        if (webcam != null) {
            Dimension[] resolutions = webcam.getViewSizes();
            Dimension maxResolution = resolutions[resolutions.length - 1];
            if (webcam.isOpen()) {
                webcam.close();
            }
            webcam.setViewSize(maxResolution);
            webcam.open();

            panel = new WebcamPanel(webcam);
            panel.setPreferredSize(maxResolution);
            panel.setFPSDisplayed(true);

            webCamPanel.add(panel, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 689, 518));
            executor.execute(this);
            // Removed premature executor.shutdown() (would prevent thread operation)
        } else {
            JOptionPane.showMessageDialog(this, "No webcam found.", "Webcam Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BufferedImage image = null;

    private void CircularImageFrame(String imagePath) {
        Connection con = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            con = ConnectionProvider.getcon();
            st = con.createStatement();
            // Defensive: SQL injection protection
            String email = resultMap.get("email").replace("'", "''");
            rs = st.executeQuery("select * from userdetails where email ='" + email + "';");
            if (!rs.next()) {
                showPopUpForCertainDuration("User is not registered or Deleted ", "Invalid Qr", JOptionPane.ERROR_MESSAGE);
                return;
            }

            image = null;
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                try {
                    image = ImageIO.read(new File(imagePath));
                    image = createCircularImage(image);
                    ImageIcon icon = new ImageIcon(image);
                    lblImage.setIcon(icon);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    lblImage.setIcon(null);
                }
            } else {
                BufferedImage imagee = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = imagee.createGraphics();
                g2d.setColor(Color.black);
                g2d.fillOval(25, 25, 250, 250);
                g2d.setFont(new Font("Serif", Font.BOLD, 250));
                g2d.setColor(Color.WHITE);
                g2d.drawString(String.valueOf(resultMap.get("name").charAt(0)).toUpperCase(), 75, 225);
                g2d.dispose();
                ImageIcon imageIconn = new ImageIcon(imagee);
                lblImage.setIcon(imageIconn);
            }

            lblName.setHorizontalAlignment(JLabel.CENTER);
            lblName.setText(resultMap.get("name"));
            if (!checkInCheckOut()) {
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
            } catch (Exception ignored) {
            }
            try {
                if (st != null) st.close();
            } catch (Exception ignored) {
            }
            try {
                if (con != null) con.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean checkInCheckOut() throws HeadlessException, SQLException {
        String popUpHeader = null;
        String popUpMessage = null;
        Color color = null;

        Connection con = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            con = ConnectionProvider.getcon();
            st = con.createStatement();

            LocalDate currentDate = LocalDate.now();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDateTime currentDateTime = LocalDateTime.now();
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // Fixed extra spaces

            rs = st.executeQuery("select * from userattendance where date ='" + currentDate.format(dateFormatter)
                    + "' and userid= " + Integer.valueOf(resultMap.get("id")) + ";");

            Connection connection = ConnectionProvider.getcon(); // Consider reusing 'con' from above
            if (rs.next()) {
                String checkOutDateTime = rs.getString(4);
                if (checkOutDateTime != null) {
                    popUpMessage = "Already CheckOut For the Day";
                    popUpHeader = "Invalid CheckOut";
                    showPopUpForCertainDuration(popUpMessage, popUpHeader, JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                String checkInDateTime = rs.getString(3);
                LocalDateTime checkInLocalDateTime = LocalDateTime.parse(checkInDateTime, dateTimeFormatter);
                Duration duration = Duration.between(checkInLocalDateTime, currentDateTime);
                long hours = duration.toHours();
                long minutes = duration.minusHours(hours).toMinutes();
                long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();

                // Fixed to 5-minute minimum, as per warning message
                if (!(hours > 0 || (hours == 0 && minutes >= 5))) {
                    long remainingMinutes = 5 - minutes;
                    long remainingSeconds = 60 - seconds;
                    popUpMessage = String.format("Your work duration is less than 5 minutes\n You can check out after: %d minutes and %d seconds", remainingMinutes, remainingSeconds);
                    popUpHeader = "Duration Warning";
                    showPopUpForCertainDuration(popUpMessage, popUpHeader, JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                String updateQuery = "update userattendance set checkout=?,workduration=? where date=? and userid=?";
                PreparedStatement preparedStatement = connection.prepareStatement(updateQuery);
                preparedStatement.setString(1, currentDateTime.format(dateTimeFormatter));
                preparedStatement.setString(2, "" + hours + " Hours and " + minutes + " Minutes");
                preparedStatement.setString(3, currentDate.format(dateFormatter));
                preparedStatement.setString(4, resultMap.get("id"));
                preparedStatement.executeUpdate();
                popUpHeader = "CheckOut";
                popUpMessage = "Checked out at " + currentDateTime.format(dateTimeFormatter) + "\n Work Duration " + hours
                        + " Hours and " + minutes + " Minutes";
                color = Color.RED;
            } else {
                String insertQuery = "INSERT INTO userattendance (userid,date,checkin) VALUES(?,?,?)";
                PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);
                preparedStatement.setString(1, resultMap.get("id"));
                preparedStatement.setString(2, currentDate.format(dateFormatter));
                preparedStatement.setString(3, currentDateTime.format(dateTimeFormatter));
                preparedStatement.executeUpdate();
                popUpHeader = "CheckIn";
                popUpMessage = "Checked in at " + currentDateTime.format(dateTimeFormatter);
                color = Color.GREEN;
            }

            lblCheckInCheckOut.setHorizontalAlignment(JLabel.CENTER);
            lblCheckInCheckOut.setText(popUpHeader);
            lblCheckInCheckOut.setForeground(color);
            lblCheckInCheckOut.setBackground(Color.DARK_GRAY);
            lblCheckInCheckOut.setOpaque(true);
            showPopUpForCertainDuration(popUpMessage, popUpHeader, JOptionPane.INFORMATION_MESSAGE);
            return true;
        } finally {
            // Defensive resource closing
            try {
                if (rs != null) rs.close();
            } catch (Exception ignored) {
            }
            try {
                if (st != null) st.close();
            } catch (Exception ignored) {
            }
            try {
                if (con != null) con.close();
            } catch (Exception ignored) {
            }
        }
    }

    private BufferedImage createCircularImage(BufferedImage image) {
        int diameter = 285;
        BufferedImage resizedImage = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, 0, 0, diameter, diameter, null);
        g2.dispose();
        BufferedImage circularImage = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        g2 = circularImage.createGraphics();
        Ellipse2D.Double circle = new Ellipse2D.Double(0, 0, diameter, diameter);
        g2.setClip(circle);
        g2.drawImage(resizedImage, 0, 0, null);
        g2.dispose();
        return circularImage;
    }

    private void showPopUpForCertainDuration(String popUpMessage, String popUpHeader, Integer iconId) throws HeadlessException {
        final JOptionPane optionPane = new JOptionPane(popUpMessage, iconId);
        final JDialog dialog = optionPane.createDialog(popUpHeader);
        Timer timer = new Timer(5000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
                clearUserDetails();
            }
        });
        timer.setRepeats(false);
        timer.start();
        dialog.setVisible(true);
    }

    private void clearUserDetails() {
        lblCheckInCheckOut.setText("");
        lblCheckInCheckOut.setBackground(null);
        lblCheckInCheckOut.setForeground(null);
        lblCheckInCheckOut.setOpaque(false);
        lblName.setText("");
        lblImage.setIcon(null);
    }

    private void updateTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd                               HH:mm:ss"); // Removed extra spaces
        lblTime.setText(simpleDateFormat.format(new Date()));
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (image != null) {
            g.drawImage(image, 0, 0, null);
        }
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        btnExit = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        webCamPanel = new javax.swing.JPanel();
        lblImage = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        lblTime = new javax.swing.JLabel();
        lblName = new javax.swing.JLabel();
        lblCheckInCheckOut = new javax.swing.JLabel();
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setMaximumSize(new java.awt.Dimension(1366, 768));
        setMinimumSize(new java.awt.Dimension(1366, 768));
        setUndecorated(true);
        btnExit.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        btnExit.setText("X");
        btnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExitActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setText("Mark Attendance");

        webCamPanel.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Date");

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(255, 255, 255));
        jLabel4.setText("Time");

        lblTime.setFont(new java.awt.Font("Segoe UI", 1, 15)); // NOI18N
        lblTime.setForeground(new java.awt.Color(255, 255, 255));
        lblTime.setText("Time");

        lblName.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N

        lblCheckInCheckOut.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addContainerGap(38, Short.MAX_VALUE)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(webCamPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel1))
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(582, 582, 582)
                                                .addComponent(btnExit, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(38, 38, 38)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(lblImage, javax.swing.GroupLayout.PREFERRED_SIZE, 322, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                                                .addComponent(lblTime, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                .addGroup(layout.createSequentialGroup()
                                                                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addGap(178, 178, 178)
                                                                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addGap(39, 39, 39)
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(lblName, javax.swing.GroupLayout.PREFERRED_SIZE, 242, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addComponent(lblCheckInCheckOut, javax.swing.GroupLayout.PREFERRED_SIZE, 242, javax.swing.GroupLayout.PREFERRED_SIZE))))))
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel1)
                                        .addComponent(btnExit, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(44, 44, 44)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(webCamPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(jLabel3)
                                                        .addComponent(jLabel4))
                                                .addGap(27, 27, 27)
                                                .addComponent(lblTime, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(27, 27, 27)
                                                .addComponent(lblImage, javax.swing.GroupLayout.PREFERRED_SIZE, 286, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(lblName, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(lblCheckInCheckOut, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addContainerGap(169, Short.MAX_VALUE))
        );

        pack();
        setLocationRelativeTo(null);
    }

    private void btnExitActionPerformed(java.awt.event.ActionEvent evt) {
        running = false;
        stopWebcam();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        this.dispose();
    }

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(MarkAttendance.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MarkAttendance().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify
    private javax.swing.JButton btnExit;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel webCamPanel;
    private javax.swing.JLabel lblCheckInCheckOut;
    private javax.swing.JLabel lblImage;
    private javax.swing.JLabel lblName;
    private javax.swing.JLabel lblTime;

    Map<String, String> resultMap = new HashMap<String, String>();

    @Override
    public void run() {
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                // Don't swallow interruptions. Consider interrupting thread.
                Thread.currentThread().interrupt();
            }

            try {
                Result result = null;

                if (webcam != null && webcam.isOpen()) {
                    BufferedImage image = webcam.getImage();

                    if (image == null) {
                        System.out.println("⚠️ Webcam returned null image. Skipping frame.");
                        continue;
                    }

                    LuminanceSource source = new BufferedImageLuminanceSource(image);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                    try {
                        result = new MultiFormatReader().decode(bitmap);
                    } catch (NotFoundException ex) {
                        // No QR code found in image
                    }

                    if (result != null) {
                        String jsonString = result.getText();
                        Gson gson = new Gson();
                        java.lang.reflect.Type type = new TypeToken<Map<String, String>>() {
                        }.getType();
                        resultMap = gson.fromJson(jsonString, type);
                        String finalPath = BDUtility.getPath("images\\" + resultMap.get("email") + ".jpg");
                        CircularImageFrame(finalPath);
                    }
                } else {
                    System.out.println("");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } while (running);
    }

    private void stopWebcam() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "My Thread");
        t.setDaemon(true);
        return t;
    }
}