/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.junit4android;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity that runs JUnit tests.
 * 
 * @author Daniel Thommes
 */
public class JunitTestRunnerActivity extends Activity {

	private LinkedHashMap<String, List<JunitTestResult>> testResultMap = new LinkedHashMap<String, List<JunitTestRunnerActivity.JunitTestResult>>();

	private ExpandableTestListAdapter testListAdapter;

	private ExpandableListView testListView;

	public static JunitTestResult testResult4Detail;

	private Button startButton;

	/**
	 * The test class to be run by this activity - can also be a test suite
	 */
	private Class<?> testClass;

	private TextView testNameTextView;

	/**
	 * {@inheritDoc}
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.junittestrunner);
		testListView = (ExpandableListView) findViewById(R.id.expandableListView);
		testListAdapter = new ExpandableTestListAdapter();
		testListView.setAdapter(testListAdapter);

		startButton = (Button) findViewById(R.id.startButton);
		testNameTextView = (TextView) findViewById(R.id.suiteNameTextView);

		testListView
				.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
					@Override
					public boolean onChildClick(ExpandableListView parent,
							View v, int groupPosition, int childPosition,
							long id) {
						JunitTestResult result = testListAdapter.getChild(
								groupPosition, childPosition);
						testResult4Detail = result;
						startActivity(new Intent(JunitTestRunnerActivity.this,
								TestResultDetailActivity.class));

						return true;
					}
				});

		try {
			testClass = loadTestClass();
			testNameTextView.setText(testClass.getName());
		} catch (Exception e) {
			startButton.setEnabled(false);
			Toast.makeText(
					JunitTestRunnerActivity.this,
					e.getClass().getSimpleName()
							+ "\nwhen loading the test (suite):\n\""
							+ e.getMessage() + "\"", Toast.LENGTH_LONG).show();
			testNameTextView.setSingleLine(false);
			testNameTextView.setText(e.getClass().getSimpleName() + ": "
					+ e.getMessage());
			testNameTextView.setTextColor(Color.RED);
			e.printStackTrace();
		}

	}

	/**
	 * TestResult for display after the test is finished
	 * 
	 * @author Daniel Thommes
	 */
	protected class JunitTestResult {
		Description description;
		List<Failure> failures = new LinkedList<Failure>();

		public JunitTestResult(Description description) {
			this.description = description;
		}

		public boolean addFailure(Failure object) {
			return failures.add(object);
		}

		@Override
		public String toString() {
			return description.getClassName();
		}

		public boolean hasFailures() {
			return !failures.isEmpty();
		}

	}

	/**
	 * @param view
	 */
	public void onStartTestClicked(View view) {
		testResultMap.clear();
		startButton.setEnabled(false);
		setProgressBarIndeterminateVisibility(true);
		new TestRunTask().execute(testClass);
	}

	/**
	 * Helper to load the test class. First tries to load the class from a
	 * String Extra in the calling intent, then from the class name as given in
	 * the meta-data tag of this activity (key 'testClass'). If there is no
	 * class given via intent or metadata falls back to
	 * <appPackageName>.AllTests class.
	 * 
	 * @return The test class to be run by this activity
	 * @throws NameNotFoundException
	 * @throws ClassNotFoundException
	 */
	public Class<?> loadTestClass() throws NameNotFoundException,
			ClassNotFoundException {
		/*************************************************************
		 * Try to get the testClass name from an extra
		 *************************************************************/
		String testSuiteClassName = getIntent().getStringExtra("testClass");
		if (testSuiteClassName == null) {
			/*************************************************************
			 * Try to load the testClass name from the activity's metadata
			 *************************************************************/
			ActivityInfo activityInfo = getPackageManager().getActivityInfo(
					getComponentName(), PackageManager.GET_META_DATA);
			Bundle bundle = activityInfo.metaData;
			if (bundle != null) {
				testSuiteClassName = bundle.getString("testClass");
			}
		}
		if (testSuiteClassName == null) {
			/*************************************************************
			 * Fall back to <packagename>.AllTests
			 *************************************************************/
			testSuiteClassName = getPackageName() + ".AllTests";
		}
		Class<?> suiteClass = getClass().forName(testSuiteClassName);
		return suiteClass;
	}

	/**
	 * Adds a result of a test to the list view
	 * 
	 * @param result
	 */
	private void addTestResult(JunitTestResult result) {
		String className = result.description.getClassName();
		List<JunitTestRunnerActivity.JunitTestResult> results = testResultMap
				.get(className);
		if (results == null) {
			results = new LinkedList<JunitTestRunnerActivity.JunitTestResult>();
			testResultMap.put(className, results);
		}
		testListView.expandGroup(testResultMap.size() - 1);
		if (testResultMap.size() > 1) {
			testListView.collapseGroup(testResultMap.size() - 2);
		}
		results.add(result);
		testListAdapter.notifyDataSetChanged();
	}

	/**
	 * {@link AsyncTask} that runs all tests in the background
	 * 
	 * @author Daniel Thommes
	 */
	private class TestRunTask extends
			AsyncTask<Class<?>, JunitTestResult, Void> {

		/**
		 * {@inheritDoc}
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		protected Void doInBackground(Class<?>... testClasses) {
			runTests(testClasses);
			return null;
		}

		/**
		 * Helper to get an Junit3 test suite's static suite method
		 * 
		 * @param clazz
		 * @return the suite method
		 */
		private Method getSuiteMethod(Class<?> clazz) {
			Method method;
			try {
				method = clazz.getMethod("suite");
			} catch (Exception e) {
				return null;
			}
			if (Modifier.isStatic(method.getModifiers())) {
				return method;
			}
			return null;
		}

		/**
		 * @param testClasses
		 */
		public void runTests(Class<?>... testClasses) {
			for (Class<?> testClass : testClasses) {
				boolean isTestCase = TestCase.class.isAssignableFrom(testClass);
				if (isTestCase) {
					/*************************************************************
					 * JUnit3 TestSuite handling because the below runner
					 * couldn't do it
					 *************************************************************/
					Method suiteMethod = getSuiteMethod(testClass);
					if (suiteMethod != null) {
						try {
							TestSuite suite = (TestSuite) suiteMethod.invoke(
									null, null);
							Enumeration tests = suite.tests();
							while (tests.hasMoreElements()) {
								Object nextElement = tests.nextElement();
								runTests(nextElement.getClass());
							}
							// Test methods will not be considered in a suite
							continue;
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				}

				/*************************************************************
				 * JUnit3 TestCases, JUnit4 Tests and Suites are run this way
				 *************************************************************/
				Request runnerRequest = Request
						.classWithoutSuiteMethod(testClass);
				Runner runner = runnerRequest.getRunner();
				RunNotifier notifier = new RunNotifier();

				notifier.addListener(new RunListener() {

					/**
					 * This result will be filled during the run and then added
					 * to the result list of this activity
					 */
					JunitTestResult result;

					@Override
					public void testFailure(Failure failure) throws Exception {
						result.addFailure(failure);
					}

					@Override
					public void testFinished(Description description)
							throws Exception {
						publishProgress(result);
					}

					@Override
					public void testStarted(Description description)
							throws Exception {
						result = new JunitTestResult(description);
					}

				});
				runner.run(notifier);
			}
		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		protected void onProgressUpdate(JunitTestResult... results) {
			addTestResult(results[0]);
		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Void result) {
			startButton.setEnabled(true);
			setProgressBarIndeterminateVisibility(false);
			testListView.collapseGroup(testResultMap.size() - 1);
		}

	}

	/**
	 * Adapter for the JunitTestResults
	 */
	public class ExpandableTestListAdapter extends BaseExpandableListAdapter {

		private final DataSetObservable mDataSetObservable = new DataSetObservable();

		public void registerDataSetObserver(DataSetObserver observer) {
			mDataSetObservable.registerObserver(observer);
		}

		public void unregisterDataSetObserver(DataSetObserver observer) {
			mDataSetObservable.unregisterObserver(observer);
		}

		/**
		 * Notifies the attached View that the underlying data has been changed
		 * and it should refresh itself.
		 */
		public void notifyDataSetChanged() {
			mDataSetObservable.notifyChanged();
		}

		public void notifyDataSetInvalidated() {
			mDataSetObservable.notifyInvalidated();
		}

		@Override
		@SuppressWarnings("unchecked")
		public List<JunitTestResult> getGroup(int groupPosition) {
			Object[] groups = testResultMap.values().toArray();
			Object group = groups[groupPosition];
			return (List<JunitTestResult>) group;
		}

		@Override
		public int getGroupCount() {
			return testResultMap.size();
		}

		@Override
		public JunitTestResult getChild(int groupPosition, int childPosition) {
			List<JunitTestResult> group = getGroup(groupPosition);
			return group.get(childPosition);
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			return getGroup(groupPosition).size();
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return true;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return childPosition;
		}

		/**
		 * Creates the view for a test method
		 * 
		 * @see android.widget.ExpandableListAdapter#getChildView(int, int,
		 *      boolean, android.view.View, android.view.ViewGroup)
		 */
		@Override
		public View getChildView(int groupPosition, int childPosition,
				boolean isLastChild, View convertView, ViewGroup parent) {
			TextView textView = getGenericView();
			JunitTestResult child = getChild(groupPosition, childPosition);
			String text = child.description.getMethodName();
			if (child.hasFailures()) {
				textView.setTextColor(Color.RED);
				text += ": \"" + child.failures.get(0).getMessage() + "\"";
			} else {
				textView.setTextColor(Color.GREEN);
			}
			textView.setText(text);
			return textView;
		}

		/**
		 * Creates the view for the test node
		 * 
		 * @see android.widget.ExpandableListAdapter#getGroupView(int, boolean,
		 *      android.view.View, android.view.ViewGroup)
		 */
		@Override
		public View getGroupView(int groupPosition, boolean isExpanded,
				View convertView, ViewGroup parent) {
			TextView textView = getGenericView();
			List<JunitTestResult> group = getGroup(groupPosition);
			boolean hasFailures = false;
			for (JunitTestResult myTestResult : group) {
				hasFailures |= myTestResult.hasFailures();
			}
			textView.setText(group.get(0).description.getClassName());
			if (hasFailures) {
				textView.setTextColor(Color.RED);
			} else {
				textView.setTextColor(Color.GREEN);
			}
			return textView;
		}

		/**
		 * Helper
		 * 
		 * @return
		 */
		private TextView getGenericView() {
			// Layout parameters for the ExpandableListView
			AbsListView.LayoutParams lp = new AbsListView.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, 64);

			TextView textView = new TextView(JunitTestRunnerActivity.this);
			textView.setLayoutParams(lp);
			// Center the text vertically
			textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
			// Set the text starting position
			textView.setPadding(60, 0, 0, 0);
			return textView;
		}

	}
}